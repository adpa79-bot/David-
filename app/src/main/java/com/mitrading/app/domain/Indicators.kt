package com.mitrading.app.domain

import com.mitrading.app.data.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Ind {

    fun sma(values: List<Double>, n: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= n) sum -= values[i - n]
            out += if (i >= n - 1) sum / n else null
        }
        return out
    }

    /** RSI de Wilder. */
    fun rsi(values: List<Double>, n: Int = 14): List<Double?> {
        val out = MutableList<Double?>(values.size) { null }
        if (values.size <= n) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val ch = values[i] - values[i - 1]
            if (ch > 0) gain += ch else loss -= ch
        }
        gain /= n
        loss /= n
        out[n] = rsiOf(gain, loss)
        for (i in n + 1 until values.size) {
            val ch = values[i] - values[i - 1]
            gain = (gain * (n - 1) + max(ch, 0.0)) / n
            loss = (loss * (n - 1) + max(-ch, 0.0)) / n
            out[i] = rsiOf(gain, loss)
        }
        return out
    }

    private fun rsiOf(gain: Double, loss: Double): Double =
        if (loss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + gain / loss)

    /** Estocástico %K(n) y %D(d). */
    fun stochastic(c: List<Candle>, n: Int = 14, d: Int = 3): Pair<List<Double?>, List<Double?>> {
        val k = MutableList<Double?>(c.size) { null }
        for (i in n - 1 until c.size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - n + 1..i) {
                hh = max(hh, c[j].h)
                ll = min(ll, c[j].l)
            }
            k[i] = if (hh - ll == 0.0) 50.0 else (c[i].c - ll) / (hh - ll) * 100.0
        }
        val dd = MutableList<Double?>(c.size) { null }
        for (i in c.indices) {
            if (i < d - 1) continue
            var s = 0.0
            var ok = true
            for (j in i - d + 1..i) {
                val v = k[j]
                if (v == null) { ok = false; break }
                s += v
            }
            if (ok) dd[i] = s / d
        }
        return k to dd
    }

    /**
     * Soportes y resistencias a partir de máximos y mínimos locales (swing points) de la ventana visible.
     * Devuelve hasta 2 resistencias por encima y 2 soportes por debajo del último precio.
     */
    fun levels(window: List<Candle>, last: Double, k: Int = 3): Pair<List<Double>, List<Double>> {
        if (window.size < 2 * k + 2) return emptyList<Double>() to emptyList()
        val highs = mutableListOf<Double>()
        val lows = mutableListOf<Double>()
        for (i in k until window.size - k) {
            var isH = true
            var isL = true
            for (j in i - k..i + k) {
                if (window[j].h > window[i].h) isH = false
                if (window[j].l < window[i].l) isL = false
            }
            if (isH) highs += window[i].h
            if (isL) lows += window[i].l
        }
        val res = highs.filter { it > last * 1.002 }.sorted()
        val sup = lows.filter { it < last * 0.998 }.sortedDescending()
        fun pick(list: List<Double>): List<Double> {
            val out = mutableListOf<Double>()
            for (v in list) {
                if (out.size >= 2) break
                if (out.all { abs(it - v) / v > 0.012 }) out += v
            }
            return out
        }
        val r = pick(res).ifEmpty { listOf(window.maxOf { it.h }).filter { it > last } }
        val s = pick(sup).ifEmpty { listOf(window.minOf { it.l }).filter { it < last } }
        return r to s
    }

    enum class TrendState(val label: String) { UP("Alcista"), DOWN("Bajista"), FLAT("Lateral") }

    data class Trend(
        val state: TrendState,
        val changePct: Double,
        val fitChangePct: Double,
        val r2: Double,
        val projection: Double,
        val a: Double,
        val b: Double,
        val closes: List<Double>
    ) {
        val strength: String get() = when {
            r2 > 0.6 -> "alta"
            r2 > 0.3 -> "media"
            else -> "baja"
        }
    }

    /** Tendencia por regresión lineal sobre los últimos [n] cierres. */
    fun trend(allCloses: List<Double>, n: Int): Trend? {
        if (allCloses.size < n / 2 || allCloses.size < 20) return null
        val closes = allCloses.takeLast(n)
        val m = closes.size
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        closes.forEachIndexed { i, v ->
            sx += i; sy += v; sxx += i.toDouble() * i; sxy += i * v
        }
        val den = m * sxx - sx * sx
        if (den == 0.0) return null
        val b = (m * sxy - sx * sy) / den
        val a = (sy - b * sx) / m
        val mean = sy / m
        var ssr = 0.0; var sst = 0.0
        closes.forEachIndexed { i, v ->
            val f = a + b * i
            ssr += (v - f) * (v - f)
            sst += (v - mean) * (v - mean)
        }
        val r2 = if (sst == 0.0) 0.0 else 1 - ssr / sst
        val fitChange = if (a != 0.0) b * (m - 1) / a * 100 else 0.0
        val change = (closes.last() - closes.first()) / closes.first() * 100
        val state = when {
            fitChange > 5 -> TrendState.UP
            fitChange < -5 -> TrendState.DOWN
            else -> TrendState.FLAT
        }
        return Trend(state, change, fitChange, r2, a + b * (2 * m - 1), a, b, closes)
    }

    fun oscState(v: Double?, hi: Double, lo: Double): String = when {
        v == null -> "—"
        v > hi -> "Sobrecompra"
        v < lo -> "Sobreventa"
        else -> "Neutral"
    }
}
