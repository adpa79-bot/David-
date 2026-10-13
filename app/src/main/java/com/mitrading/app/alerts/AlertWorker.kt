package com.mitrading.app.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mitrading.app.MainActivity
import com.mitrading.app.R
import com.mitrading.app.data.AlertCond
import com.mitrading.app.data.AlertRule
import com.mitrading.app.data.AssetType
import com.mitrading.app.data.Instrument
import com.mitrading.app.data.LogEntry
import com.mitrading.app.data.Repo
import com.mitrading.app.data.Store
import com.mitrading.app.data.Timeframe
import com.mitrading.app.domain.Ind
import com.mitrading.app.ui.fmt
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Comprueba las alertas cada ~15 minutos en segundo plano. */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        checkAlerts(applicationContext)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "alertas"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("alertas", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AlertWorker>().build())
        }

        /** Evalúa todas las alertas activas. Devuelve cuántas se dispararon. */
        suspend fun checkAlerts(context: Context): Int {
            val store = Store(context)
            val repo = Repo(store)
            val all = store.alerts()
            val active = all.filter { it.enabled }
            if (active.isEmpty()) return 0
            val updated = all.associateBy { it.id }.toMutableMap()
            var fired = 0
            for ((symbol, rules) in active.groupBy { it.symbol }) {
                val inst = Instrument(symbol, rules.first().name, AssetType.OTHER)
                val series = runCatching { repo.candles(inst, Timeframe.D1, force = true) }.getOrNull() ?: continue
                if (series.candles.size < 2) continue
                val quote = runCatching { repo.quote(inst) }.getOrNull()
                val price = quote?.price ?: series.candles.last().c
                val closes = series.candles.map { it.c }.toMutableList()
                closes[closes.size - 1] = price
                val rsi = Ind.rsi(closes).lastOrNull()
                val ma50 = Ind.sma(closes, 50)
                val prevClose = closes[closes.size - 2]
                val prevMa = ma50.getOrNull(ma50.size - 2)
                val curMa = ma50.lastOrNull()
                val pct = quote?.changePct ?: ((price - prevClose) / prevClose * 100)

                for (rule in rules) {
                    val hit = when (rule.cond) {
                        AlertCond.ABOVE -> price >= rule.value
                        AlertCond.BELOW -> price <= rule.value
                        AlertCond.RSI_HIGH -> rsi != null && rsi > rule.value
                        AlertCond.RSI_LOW -> rsi != null && rsi < rule.value
                        AlertCond.PCT -> abs(pct) >= rule.value
                        AlertCond.MA50_CROSS -> prevMa != null && curMa != null &&
                            ((prevClose - prevMa) * (price - curMa) < 0)
                    }
                    if (hit && rule.armed) {
                        val text = describeHit(rule, price, rsi, pct)
                        notify(context, rule.id.toInt(), rule.name, text)
                        store.addLog(LogEntry(System.currentTimeMillis(), "${rule.name}: $text"))
                        updated[rule.id] = rule.copy(armed = false, lastTriggered = System.currentTimeMillis())
                        fired++
                    } else if (!hit && !rule.armed) {
                        // La condición dejó de cumplirse: se rearma para el próximo aviso.
                        updated[rule.id] = rule.copy(armed = true)
                    }
                }
            }
            store.saveAlerts(all.map { updated[it.id] ?: it })
            return fired
        }

        fun describe(rule: AlertRule): String = when (rule.cond) {
            AlertCond.ABOVE -> "Precio igual o superior a ${fmt(rule.value)}"
            AlertCond.BELOW -> "Precio igual o inferior a ${fmt(rule.value)}"
            AlertCond.RSI_HIGH -> "RSI (14) por encima de ${fmt(rule.value, 0)}"
            AlertCond.RSI_LOW -> "RSI (14) por debajo de ${fmt(rule.value, 0)}"
            AlertCond.PCT -> "Variación diaria mayor de ±${fmt(rule.value, 1)} %"
            AlertCond.MA50_CROSS -> "El precio cruza la media de 50 sesiones"
        }

        private fun describeHit(rule: AlertRule, price: Double, rsi: Double?, pct: Double): String = when (rule.cond) {
            AlertCond.ABOVE -> "ha subido a ${fmt(price)} (alerta ≥ ${fmt(rule.value)})"
            AlertCond.BELOW -> "ha bajado a ${fmt(price)} (alerta ≤ ${fmt(rule.value)})"
            AlertCond.RSI_HIGH -> "RSI en ${fmt(rsi ?: 0.0, 1)}: zona de sobrecompra"
            AlertCond.RSI_LOW -> "RSI en ${fmt(rsi ?: 0.0, 1)}: zona de sobreventa"
            AlertCond.PCT -> "se mueve un ${fmt(pct, 2)} % hoy (precio ${fmt(price)})"
            AlertCond.MA50_CROSS -> "el precio (${fmt(price)}) ha cruzado la media de 50 sesiones"
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Alertas de precio", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }

        @SuppressLint("MissingPermission")
        fun notify(context: Context, id: Int, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Mi trading · $title")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (e: SecurityException) {
                // Sin permiso de notificaciones.
            }
        }
    }
}
