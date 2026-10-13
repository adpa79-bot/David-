package com.mitrading.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitrading.app.alerts.AlertWorker
import com.mitrading.app.data.AlertCond
import com.mitrading.app.data.Importer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var symbol by remember { mutableStateOf(vm.alertPresetSymbol ?: vm.selected?.symbol ?: vm.favorites.firstOrNull()?.symbol) }
    var cond by remember { mutableStateOf(AlertCond.ABOVE) }
    var valueText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    LaunchedEffect(Unit) {
        vm.reloadAlerts()
        vm.alertPresetSymbol = null
    }

    val inst = vm.favorites.firstOrNull { it.symbol == symbol }
        ?: vm.selected?.takeIf { it.symbol == symbol }
    val q = symbol?.let { vm.quotes[it] }

    // Valor por defecto según la condición
    LaunchedEffect(cond, symbol) {
        valueText = when (cond) {
            AlertCond.RSI_HIGH -> "70"
            AlertCond.RSI_LOW -> "30"
            AlertCond.PCT -> "3"
            AlertCond.MA50_CROSS -> ""
            else -> q?.let { fmt(it.price, if (it.price < 2) 4 else 2).replace(".", "") } ?: ""
        }
    }

    Row(Modifier.fillMaxSize()) {
        // ---------------- Formulario ----------------
        Column(
            Modifier.width(430.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            ScreenTitle("Nueva alerta")
            Muted("Te avisará con una notificación en la tablet (comprobación cada ~15 min).", Modifier.padding(top = 4.dp))

            if (!notifGranted) {
                Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Text("Las notificaciones están desactivadas.", color = C.down, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Permitir notificaciones", {
                        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
            }

            Muted("Instrumento (de tus favoritos)", Modifier.padding(top = 18.dp, bottom = 8.dp))
            val options = (vm.favorites.toList() + listOfNotNull(vm.selected)).distinctBy { it.symbol }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { i -> Pill(i.name, i.symbol == symbol, { symbol = i.symbol }) }
            }

            Muted("Condición", Modifier.padding(top = 18.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AlertCond.entries.forEach { c -> Pill(c.label, c == cond, { cond = c }) }
            }

            if (cond != AlertCond.MA50_CROSS) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            when (cond) {
                                AlertCond.RSI_HIGH, AlertCond.RSI_LOW -> "Nivel de RSI"
                                AlertCond.PCT -> "Porcentaje (%)"
                                else -> "Precio"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            if (q != null && inst != null) {
                Muted("Precio actual de ${inst.name}: ${fmtPrice(q.price)}${curSuffix(q.currency)}", Modifier.padding(top = 8.dp))
            }
            PrimaryButton(
                "Crear alerta",
                {
                    val target = inst
                    val v = if (cond == AlertCond.MA50_CROSS) 0.0 else Importer.parseNumber(valueText)
                    message = when {
                        target == null -> "Elige un instrumento."
                        v == null -> "Introduce un valor válido."
                        else -> {
                            vm.addAlert(target, cond, v)
                            "Alerta creada: ${target.name} · " + AlertWorker.describe(vm.alerts.first())
                        }
                    }
                },
                Modifier.padding(top = 16.dp).fillMaxWidth()
            )
            message?.let { Muted(it, Modifier.padding(top = 8.dp), 13) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))

        // ---------------- Lista ----------------
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Mis alertas")
                Spacer(Modifier.width(12.dp))
                Muted("${vm.alerts.count { it.enabled }} activas de ${vm.alerts.size}", Modifier.weight(1f))
                SecondaryButton(if (vm.checkingAlerts) "Comprobando…" else "Comprobar ahora", { vm.checkAlertsNow(context) }, icon = Ic.refresh)
            }
            Spacer(Modifier.height(10.dp))
            if (vm.alerts.isEmpty()) Muted("No tienes alertas. Crea la primera con el formulario.", size = 14)
            LazyColumn(Modifier.weight(1f)) {
                items(vm.alerts, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val active = a.enabled
                        val col = when (a.cond) {
                            AlertCond.RSI_HIGH, AlertCond.RSI_LOW -> C.sup
                            else -> C.res
                        }
                        Box(
                            Modifier.size(36.dp).background(if (active) col.copy(alpha = 0.12f) else C.chip, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Ic.bell, null, tint = if (active) col else C.muted, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(0.9f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.text)
                            Muted(a.symbol, size = 11)
                        }
                        Column(Modifier.weight(1.4f)) {
                            Text(AlertWorker.describe(a), fontSize = 13.sp, color = C.text2)
                            if (a.lastTriggered > 0) Muted("Último aviso: ${dateTime(a.lastTriggered / 1000, "dd/MM HH:mm")}", size = 11)
                        }
                        Switch(
                            checked = a.enabled,
                            onCheckedChange = { vm.toggleAlert(a) },
                            colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = Color.White)
                        )
                        IconButton(onClick = { vm.removeAlert(a) }) {
                            Icon(Ic.trash, contentDescription = "Eliminar alerta", tint = C.muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
                }
            }
            if (vm.alertLog.isNotEmpty()) {
                SectionTitle("Disparadas recientemente", Modifier.padding(top = 12.dp, bottom = 6.dp))
                vm.alertLog.take(4).forEach { e ->
                    Row(
                        Modifier.padding(vertical = 3.dp).fillMaxWidth()
                            .background(C.up.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("${dateTime(e.time / 1000, "dd/MM HH:mm")}  ", fontSize = 12.sp, color = C.muted, fontFamily = Mono)
                        Text(e.text, fontSize = 13.sp, color = C.text)
                    }
                }
            }
        }
    }
}
