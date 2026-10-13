package com.mitrading.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitrading.app.data.Candle
import com.mitrading.app.data.Timeframe
import com.mitrading.app.domain.Ind
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Estado del zoom/desplazamiento del gráfico, para poder reiniciarlo desde fuera. */
class ChartViewState {
    var visible by mutableFloatStateOf(90f)
    var offset by mutableFloatStateOf(0f)
    var cross by mutableStateOf<Int?>(null)
    fun reset() { visible = 90f; offset = 0f; cross = null }
}

@Composable
fun PriceChart(
    candles: List<Candle>,
    tf: Timeframe,
    showMa: Boolean,
    showSr: Boolean,
    osc: Osc,
    state: ChartViewState,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Muted("Sin datos") }
        return
    }
    val closes = remember(candles) { candles.map { it.c } }
    val ma20 = remember(candles) { Ind.sma(closes, 20) }
    val ma50 = remember(candles) { Ind.sma(closes, 50) }
    val ma200 = remember(candles) { Ind.sma(closes, 200) }
    val rsi = remember(candles) { Ind.rsi(closes) }
    val sto = remember(candles) { Ind.stochastic(candles) }

    val density = LocalDensity.current
    val axisW = with(density) { 64.dp.toPx() }
    val textPx = with(density) { 11.sp.toPx() }
    val smallPx = with(density) { 10.sp.toPx() }
    val paint = remember { Paint().apply { isAntiAlias = true } }

    val n = candles.size
    val minVis = min(12, n)
    val maxVis = min(400, n)

    fun window(): Pair<Int, Int> {
        val vis = state.visible.roundToInt().coerceIn(minVis, maxVis)
        val off = state.offset.roundToInt().coerceIn(0, n - vis)
        val end = n - off
        return (end - vis) to end
    }

    Canvas(
        modifier
            .pointerInput(candles) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val plotW = size.width - axisW
                    state.visible = (state.visible / zoom).coerceIn(minVis.toFloat(), maxVis.toFloat())
                    val cw = plotW / state.visible
                    val vis = state.visible.roundToInt().coerceIn(minVis, maxVis)
                    state.offset = (state.offset + pan.x / cw).coerceIn(0f, (n - vis).toFloat())
                }
            }
            .pointerInput(candles) {
                detectTapGestures { p ->
                    val plotW = size.width - axisW
                    if (p.x > plotW) { state.cross = null; return@detectTapGestures }
                    val (s, e) = window()
                    val cw = plotW / (e - s)
                    val idx = (s + floor(p.x / cw).toInt()).coerceIn(s, e - 1)
                    state.cross = if (state.cross == idx) null else idx
                }
            }
    ) {
        val (start, end) = window()
        val vis = end - start
        val plotW = size.width - axisW
        val cw = plotW / vis
        val bw = max(1f, cw * 0.62f)

        val gap = 10.dp.toPx()
        val priceH = size.height * 0.62f
        val volTop = priceH + gap
        val volH = size.height * 0.11f
        val oscTop = volTop + volH + gap + 14.dp.toPx()
        val oscH = size.height - oscTop

        // ----- Escala de precios -----
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        for (i in start until end) {
            hi = max(hi, candles[i].h); lo = min(lo, candles[i].l)
            if (showMa) for (m in listOf(ma20, ma50, ma200)) m[i]?.let { hi = max(hi, it); lo = min(lo, it) }
        }
        val pad = (hi - lo) * 0.06 + 1e-9
        hi += pad; lo -= pad
        fun y(p: Double): Float = ((hi - p) / (hi - lo) * priceH).toFloat()
        fun x(i: Int): Float = (i - start + 0.5f) * cw

        // Rejilla y eje
        paint.textSize = textPx
        paint.typeface = Typeface.MONOSPACE
        for (t in 1..5) {
            val yy = priceH * t / 6
            drawLine(C.border, Offset(0f, yy), Offset(plotW, yy), 1f)
            val v = hi - (hi - lo) * t / 6
            text(fmtPrice(v), plotW + 6f, yy + textPx / 3, C.muted, paint)
        }
        drawLine(C.border, Offset(plotW, 0f), Offset(plotW, size.height), 1f)

        // Velas
        for (i in start until end) {
            val c = candles[i]
            val col = if (c.c >= c.o) C.up else C.down
            val cx = x(i)
            drawLine(col, Offset(cx, y(c.h)), Offset(cx, y(c.l)), max(1f, cw * 0.12f).coerceAtMost(2f))
            val top = y(max(c.o, c.c))
            val h = max(1f, kotlin.math.abs(y(c.o) - y(c.c)))
            drawRect(col, Offset(cx - bw / 2, top), Size(bw, h))
        }

        // Medias móviles
        if (showMa) {
            maLine(ma20, start, end, ::x, ::y, C.ma20, null)
            maLine(ma50, start, end, ::x, ::y, C.ma50, null)
            maLine(ma200, start, end, ::x, ::y, C.ma200, PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }

        // Soportes y resistencias
        val last = candles[end - 1].c
        if (showSr) {
            val (res, sup) = Ind.levels(candles.subList(start, end), last)
            res.forEachIndexed { k, v -> level(v, "R${k + 1}", C.res, ::y, plotW, paint, smallPx) }
            sup.forEachIndexed { k, v -> level(v, "S${k + 1}", C.sup, ::y, plotW, paint, smallPx) }
            paint.textSize = textPx
        }

        // Último precio
        val lastCol = if (candles[end - 1].c >= candles[end - 1].o) C.up else C.down
        val ly = y(last).coerceIn(0f, priceH)
        drawLine(lastCol, Offset(0f, ly), Offset(plotW, ly), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
        drawRect(lastCol, Offset(plotW, ly - textPx * 0.85f), Size(axisW, textPx * 1.7f))
        paint.isFakeBoldText = true
        text(fmtPrice(last), plotW + 6f, ly + textPx / 3, Color.White, paint)
        paint.isFakeBoldText = false

        // ----- Volumen -----
        var vmax = 0.0
        for (i in start until end) vmax = max(vmax, candles[i].v)
        text("Volumen", 0f, volTop + textPx, C.muted, paint)
        if (vmax > 0) {
            for (i in start until end) {
                val c = candles[i]
                val h = (c.v / vmax * volH).toFloat()
                drawRect(
                    (if (c.c >= c.o) C.up else C.down).copy(alpha = 0.5f),
                    Offset(x(i) - bw / 2, volTop + volH - h), Size(bw, h)
                )
            }
        }

        // ----- Oscilador -----
        val isRsi = osc == Osc.RSI
        val hiL = if (isRsi) 70.0 else 80.0
        val loL = if (isRsi) 30.0 else 20.0
        fun oy(v: Double): Float = oscTop + ((100 - v) / 100 * oscH).toFloat()
        drawRect(C.res.copy(alpha = 0.07f), Offset(0f, oscTop), Size(plotW, oy(hiL) - oscTop))
        drawRect(C.sup.copy(alpha = 0.07f), Offset(0f, oy(loL)), Size(plotW, oscTop + oscH - oy(loL)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        drawLine(C.borderStrong, Offset(0f, oy(hiL)), Offset(plotW, oy(hiL)), 1f, pathEffect = dash)
        drawLine(C.borderStrong, Offset(0f, oy(loL)), Offset(plotW, oy(loL)), 1f, pathEffect = dash)
        drawLine(C.border, Offset(0f, oscTop + oscH), Offset(plotW, oscTop + oscH), 1f)
        text(fmt(hiL, 0), plotW + 6f, oy(hiL) + textPx / 3, C.res, paint)
        text(fmt(loL, 0), plotW + 6f, oy(loL) + textPx / 3, C.sup, paint)
        val main = if (isRsi) rsi else sto.first
        oscLine(main, start, end, ::x, ::oy, C.sup)
        if (!isRsi) oscLine(sto.second, start, end, ::x, ::oy, C.res)

        // ----- Retícula (al tocar) -----
        val cross = state.cross
        if (cross != null && cross in start until end) {
            val c = candles[cross]
            val cx = x(cross)
            drawLine(C.muted, Offset(cx, 0f), Offset(cx, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            val pattern = when (tf) {
                Timeframe.H1, Timeframe.H4 -> "dd/MM/yy HH:mm"
                Timeframe.D1, Timeframe.W1 -> "dd/MM/yyyy"
                Timeframe.MN -> "MM/yyyy"
                Timeframe.Y1 -> "yyyy"
            }
            val lines = listOf(
                dateTime(c.t, pattern),
                "A ${fmtPrice(c.o)}  M ${fmtPrice(c.h)}",
                "m ${fmtPrice(c.l)}  C ${fmtPrice(c.c)}",
                "Var ${pct((c.c - c.o) / c.o * 100)}"
            )
            val boxW = lines.maxOf { paint.measureText(it) } + 16f
            val boxX = if (cx + boxW + 12f < plotW) cx + 8f else cx - boxW - 8f
            drawRect(Color.White, Offset(boxX, 6f), Size(boxW, textPx * 1.35f * lines.size + 10f))
            drawRect(C.borderStrong, Offset(boxX, 6f), Size(boxW, textPx * 1.35f * lines.size + 10f), style = Stroke(1f))
            lines.forEachIndexed { k, s -> text(s, boxX + 8f, 6f + textPx * 1.35f * (k + 1), C.text, paint) }
        }
    }
}

private fun DrawScope.text(s: String, x: Float, y: Float, color: Color, paint: Paint) {
    paint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
}

private fun DrawScope.maLine(
    values: List<Double?>, start: Int, end: Int,
    x: (Int) -> Float, y: (Double) -> Float, color: Color, effect: PathEffect?
) {
    val path = Path()
    var started = false
    for (i in start until end) {
        val v = values[i] ?: continue
        if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
    }
    if (started) drawPath(path, color, style = Stroke(width = 2f, pathEffect = effect))
}

private fun DrawScope.oscLine(
    values: List<Double?>, start: Int, end: Int,
    x: (Int) -> Float, y: (Double) -> Float, color: Color
) {
    val path = Path()
    var started = false
    for (i in start until end) {
        val v = values[i] ?: continue
        if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
    }
    if (started) drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.level(
    v: Double, name: String, color: Color, y: (Double) -> Float, plotW: Float, paint: Paint, textPx: Float
) {
    val yy = y(v)
    drawLine(color, Offset(0f, yy), Offset(plotW, yy), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    paint.textSize = textPx
    paint.isFakeBoldText = true
    text(name, 4f, yy - 4f, color, paint)
    drawRect(color, Offset(plotW, yy - textPx * 0.8f), Size(size.width - plotW, textPx * 1.6f))
    text(fmtPrice(v), plotW + 6f, yy + textPx / 3, Color.White, paint)
    paint.isFakeBoldText = false
}

/** Minigráfico de tendencia (cierres + recta de regresión). */
@Composable
fun TrendSpark(trend: Ind.Trend, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cl = trend.closes
        if (cl.size < 2) return@Canvas
        val mn = cl.min()
        val mx = cl.max()
        val r = (mx - mn).takeIf { it > 0 } ?: 1.0
        fun yy(v: Double) = (2f + (mx - v) / r * (size.height - 4f)).toFloat()
        val path = Path()
        cl.forEachIndexed { i, v ->
            val xx = i * size.width / (cl.size - 1)
            if (i == 0) path.moveTo(xx, yy(v)) else path.lineTo(xx, yy(v))
        }
        drawPath(path, C.muted, style = Stroke(width = 1.5f))
        val y0 = yy(trend.a)
        val y1 = yy(trend.a + trend.b * (cl.size - 1))
        drawLine(color, Offset(0f, y0), Offset(size.width, y1), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
    }
}
