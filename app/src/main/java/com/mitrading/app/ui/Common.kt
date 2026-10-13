package com.mitrading.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------
//  Colores (tema claro)
// ------------------------------------------------------------------
object C {
    val bg = Color(0xFFFFFFFF)
    val rail = Color(0xFFF3F5F9)
    val surface = Color(0xFFF7F9FC)
    val chip = Color(0xFFE8EEF7)
    val border = Color(0xFFE3E8F0)
    val borderStrong = Color(0xFFD3DAE5)
    val text = Color(0xFF111827)
    val text2 = Color(0xFF374151)
    val muted = Color(0xFF5B6678)
    val accent = Color(0xFF2563EB)
    val up = Color(0xFF087F5B)
    val down = Color(0xFFD92D3A)
    val res = Color(0xFFB45309)
    val sup = Color(0xFF0369A1)
    val ma20 = Color(0xFFD97706)
    val ma50 = Color(0xFF7C3AED)
    val ma200 = Color(0xFF111827)
    val star = Color(0xFFD97706)
    val purple = Color(0xFF6D28D9)
    val green2 = Color(0xFF047857)
}

val Mono = FontFamily.Monospace

@Composable
fun MiTradingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.accent,
            onPrimary = Color.White,
            background = C.bg,
            surface = C.bg,
            onSurface = C.text,
            onBackground = C.text,
            surfaceVariant = C.surface,
            outline = C.borderStrong
        ),
        content = content
    )
}

// ------------------------------------------------------------------
//  Formato de números y fechas (es-ES)
// ------------------------------------------------------------------
private val esSymbols = DecimalFormatSymbols(Locale.forLanguageTag("es-ES"))

fun fmt(v: Double, decimals: Int = 2): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
    return DecimalFormat(pattern, esSymbols).format(v)
}

/** Decimales adecuados según el tamaño del precio. */
fun fmtPrice(v: Double): String = fmt(v, if (kotlin.math.abs(v) < 2) 4 else 2)

fun pct(v: Double): String = (if (v >= 0) "+" else "") + fmt(v) + " %"

fun curSuffix(c: String): String = when (c) {
    "EUR" -> " €"
    "USD" -> " $"
    "GBP" -> " £"
    "GBp", "GBX" -> " p"
    "CHF" -> " CHF"
    "" -> ""
    else -> " $c"
}

fun dateTime(epochSec: Long, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-ES"))
        .format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))

fun ago(epochSec: Long): String {
    val d = System.currentTimeMillis() / 1000 - epochSec
    return when {
        d < 0 -> dateTime(epochSec, "dd/MM HH:mm")
        d < 3600 -> "hace ${d / 60} min"
        d < 86_400 -> "hace ${d / 3600} h"
        d < 7 * 86_400 -> "hace ${d / 86_400} d"
        else -> dateTime(epochSec, "dd/MM/yyyy")
    }
}

// ------------------------------------------------------------------
//  Iconos (dibujados a partir de trazados SVG)
// ------------------------------------------------------------------
private fun icon(name: String, path: String, filled: Boolean = false, stroke: Float = 1.8f): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(path),
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = stroke,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()

object Ic {
    private const val STAR = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"
    val star = icon("star", STAR, filled = true)
    val starOutline = icon("starOutline", STAR)
    val search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16.5 16.5L21 21", stroke = 2f)
    val bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10.3 21a1.9 1.9 0 0 0 3.4 0")
    val news = icon("news", "M3 4h18v16H3z M7 8h10 M7 12h10 M7 16h6")
    val wallet = icon("wallet", "M3 8h18v12H3z M8 8V5h8v3 M3 13h18")
    val settings = icon("settings", "M4 7h9 M17 7h3 M15 5v4 M4 17h3 M11 17h9 M9 15v4", stroke = 2f)
    val back = icon("back", "M15 18l-6-6 6-6", stroke = 2f)
    val trash = icon("trash", "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13")
    val plus = icon("plus", "M12 5v14 M5 12h14", stroke = 2f)
    val refresh = icon("refresh", "M20 12a8 8 0 1 1-2.3-5.7 M20 4v4h-4", stroke = 2f)
    val external = icon("external", "M14 4h6v6 M20 4l-9 9 M18 14v6H4V6h6")
    val upload = icon("upload", "M12 16V4 M7 9l5-5 5 5 M4 20h16", stroke = 2f)
    val zoomReset = icon("zoomReset", "M4 9V4h5 M20 9V4h-5 M4 15v5h5 M20 15v5h-5")
    val logo = icon("logo", "M6 4v16 M4 8h4v7H4z M12 3v14 M10 6h4v6h-4z M18 7v14 M16 10h4v7h-4z", stroke = 2f)
}

// ------------------------------------------------------------------
//  Componentes comunes
// ------------------------------------------------------------------
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, round: Boolean = false) {
    val shape = RoundedCornerShape(if (round) 18.dp else 8.dp)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) C.accent else C.surface)
            .border(1.dp, if (selected) C.accent else C.borderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else C.text2,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(C.surface)
            .border(1.dp, C.border, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = C.text)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 12) {
    Text(text, modifier = modifier, color = C.muted, fontSize = size.sp)
}

@Composable
fun Tag(text: String, fg: Color, bg: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, C.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.text2),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = C.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = C.text2, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
    }
}

val monoStyle = TextStyle(fontFamily = Mono)
