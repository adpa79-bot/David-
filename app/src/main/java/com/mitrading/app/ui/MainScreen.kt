package com.mitrading.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitrading.app.data.AssetType
import com.mitrading.app.data.Instrument
import com.mitrading.app.data.NewsItem
import com.mitrading.app.data.Timeframe
import com.mitrading.app.domain.Ind

@Composable
fun MainScreen(vm: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        FavoritesPanel(vm, Modifier.width(290.dp).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        ChartPanel(vm, Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        DetailPanel(vm, Modifier.width(300.dp).fillMaxHeight())
    }
}

// =====================================================================
//  Favoritos
// =====================================================================
@Composable
private fun FavoritesPanel(vm: AppViewModel, modifier: Modifier) {
    var filter by remember { mutableStateOf("all") }
    Column(modifier) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mi trading", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = C.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshAll() }) {
                    Icon(Ic.refresh, contentDescription = "Actualizar", tint = C.muted, modifier = Modifier.size(20.dp))
                }
            }
            Muted(if (vm.lastRefresh > 0) "Actualizado ${dateTime(vm.lastRefresh / 1000, "HH:mm")}" else "Cargando cotizaciones…")
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.surface)
                    .border(1.dp, C.borderStrong, RoundedCornerShape(10.dp))
                    .clickable { vm.screen = Screen.SEARCH }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.search, contentDescription = null, tint = C.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buscar y añadir valores", color = C.muted, fontSize = 14.sp)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("all" to "Todos", "acc" to "Acciones", "ind" to "Índices", "mat" to "Mat. primas / ETF").forEach { (k, l) ->
                Pill(l, filter == k, { filter = k }, round = true)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
        val list = vm.favorites.filter {
            when (filter) {
                "acc" -> it.type == AssetType.STOCK
                "ind" -> it.type == AssetType.INDEX
                "mat" -> it.type == AssetType.FUTURE || it.type == AssetType.ETF || it.type == AssetType.OTHER
                else -> true
            }
        }
        if (vm.favorites.isEmpty()) {
            Muted("Aún no tienes favoritos. Usa «Buscar» para añadirlos.", Modifier.padding(16.dp), 13)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.symbol }) { inst -> FavoriteRow(vm, inst) }
        }
    }
}

@Composable
private fun FavoriteRow(vm: AppViewModel, inst: Instrument) {
    val selected = vm.selected?.symbol == inst.symbol
    val q = vm.quotes[inst.symbol]
    val hasAlert = vm.alerts.any { it.symbol == inst.symbol && it.enabled }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) C.surface else Color.Transparent)
            .clickable { vm.select(inst) }
            .padding(end = 14.dp)
            .heightIn(min = 58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(42.dp).background(if (selected) C.accent else Color.Transparent))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    inst.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (hasAlert) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Ic.bell, contentDescription = "Alerta activa", tint = C.res, modifier = Modifier.size(13.dp))
                }
            }
            Text("${inst.symbol} · ${inst.type.label}", fontSize = 11.sp, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (q != null) {
                val up = q.change >= 0
                Text(fmtPrice(q.price) + curSuffix(q.currency), fontFamily = Mono, fontSize = 13.sp, color = C.text)
                Text(
                    (if (up) "▲ " else "▼ ") + pct(q.changePct), fontFamily = Mono, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = if (up) C.up else C.down
                )
            } else {
                Text("—", color = C.muted, fontFamily = Mono)
            }
        }
    }
}

// =====================================================================
//  Gráfico
// =====================================================================
@Composable
private fun ChartPanel(vm: AppViewModel, modifier: Modifier) {
    val inst = vm.selected
    if (inst == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Muted("Selecciona un valor", size = 15) }
    } else {
        ChartPanelContent(vm, inst, modifier)
    }
}

@Composable
private fun ChartPanelContent(vm: AppViewModel, inst: Instrument, modifier: Modifier) {
    val context = LocalContext.current
    val chartState = remember(inst.symbol, vm.tf) { ChartViewState() }
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        val q = vm.quotes[inst.symbol]
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(inst.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    Muted("${inst.symbol} · ${inst.type.label}${if (inst.exchange.isNotBlank()) " · " + inst.exchange else ""}")
                }
                if (q != null) {
                    val up = q.change >= 0
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtPrice(q.price) + curSuffix(q.currency), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = C.text)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            (if (up) "▲ +" else "▼ ") + fmtPrice(q.change) + " (" + pct(q.changePct) + ")",
                            fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = if (up) C.up else C.down, modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
            val fav = vm.isFavorite(inst.symbol)
            IconButton(onClick = { vm.toggleFavorite(inst) }) {
                Icon(if (fav) Ic.star else Ic.starOutline, contentDescription = "Favorito", tint = C.star)
            }
            var brokerMenu by remember { mutableStateOf(false) }
            Box {
                SecondaryButton("Operar", { brokerMenu = true }, icon = Ic.external)
                DropdownMenu(expanded = brokerMenu, onDismissRequest = { brokerMenu = false }) {
                    DropdownMenuItem(text = { Text("Abrir XTB") }, onClick = { brokerMenu = false; vm.openBroker(context, "XTB") })
                    DropdownMenuItem(text = { Text("Abrir Trade Republic") }, onClick = { brokerMenu = false; vm.openBroker(context, "Trade Republic") })
                }
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton("Crear alerta", {
                vm.alertPresetSymbol = inst.symbol
                vm.screen = Screen.ALERTS
            }, icon = Ic.bell)
        }

        // Barra de herramientas
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Timeframe.entries.forEach { t -> Pill(t.label, vm.tf == t, { vm.changeTf(t) }) }
            Box(Modifier.width(1.dp).height(24.dp).background(C.borderStrong))
            Pill("Medias", vm.showMa, { vm.showMa = !vm.showMa })
            Pill("Sop./Resist.", vm.showSr, { vm.showSr = !vm.showSr })
            Box(Modifier.width(1.dp).height(24.dp).background(C.borderStrong))
            Pill("RSI 14", vm.osc == Osc.RSI, { vm.osc = Osc.RSI })
            Pill("Estocástico", vm.osc == Osc.STOCH, { vm.osc = Osc.STOCH })
            IconButton(onClick = { chartState.reset() }) {
                Icon(Ic.zoomReset, contentDescription = "Restablecer zoom", tint = C.muted, modifier = Modifier.size(20.dp))
            }
        }

        // Leyenda
        val closes = vm.candles.map { it.c }
        val ma20 = Ind.sma(closes, 20).lastOrNull()
        val ma50 = Ind.sma(closes, 50).lastOrNull()
        val ma200 = Ind.sma(closes, 200).lastOrNull()
        val rsi = Ind.rsi(closes).lastOrNull()
        val stoch = if (vm.candles.isNotEmpty()) Ind.stochastic(vm.candles).first.lastOrNull() else null
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted("Velas ${vm.tf.desc}")
            if (vm.showMa) {
                Text("MM20 ${ma20?.let { fmtPrice(it) } ?: "—"}", color = C.ma20, fontFamily = Mono, fontSize = 11.sp)
                Text("MM50 ${ma50?.let { fmtPrice(it) } ?: "—"}", color = C.ma50, fontFamily = Mono, fontSize = 11.sp)
                Text("MM200 ${ma200?.let { fmtPrice(it) } ?: "—"}", color = C.ma200, fontFamily = Mono, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            val oscVal = if (vm.osc == Osc.RSI) rsi else stoch
            val st = if (vm.osc == Osc.RSI) Ind.oscState(oscVal, 70.0, 30.0) else Ind.oscState(oscVal, 80.0, 20.0)
            val col = when (st) { "Sobrecompra" -> C.res; "Sobreventa" -> C.sup; else -> C.muted }
            Text(
                "${if (vm.osc == Osc.RSI) "RSI" else "Estocástico"} ${oscVal?.let { fmt(it, 1) } ?: "—"} · $st",
                color = col, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            when {
                vm.chartLoading && vm.candles.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.accent)
                vm.chartError != null && vm.candles.isEmpty() ->
                    Muted(vm.chartError ?: "", Modifier.align(Alignment.Center), 14)
                else -> PriceChart(vm.candles, vm.tf, vm.showMa, vm.showSr, vm.osc, chartState, Modifier.fillMaxSize())
            }
            if (vm.chartLoading && vm.candles.isNotEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), color = C.accent, strokeWidth = 2.dp)
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Muted("Fuente: ${vm.chartSource} · Pellizca para zoom, desliza para moverte, toca una vela para ver sus datos.", size = 11)
        }
    }
}

// =====================================================================
//  Panel derecho: tendencia, señales, objetivo, noticias
// =====================================================================
@Composable
private fun DetailPanel(vm: AppViewModel, modifier: Modifier) {
    val inst = vm.selected ?: return
    val uri = LocalUriHandler.current
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TrendCard(vm)
        SignalsCard(vm)
        if (inst.type == AssetType.STOCK) TargetCard(vm) else ForecastNote(inst)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Noticias", Modifier.weight(1f))
                Text(
                    "Ver todas", color = C.accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.screen = Screen.NEWS }.padding(8.dp)
                )
            }
            if (vm.instNews.isEmpty()) Muted("Sin noticias recientes.")
            vm.instNews.take(4).forEach { n -> MiniNews(n) { if (n.url.isNotBlank()) runCatching { uri.openUri(n.url) } } }
        }
    }
}

@Composable
private fun TrendCard(vm: AppViewModel) {
    val closes = vm.dailyCandles.map { it.c }
    val currency = vm.quotes[vm.selected?.symbol]?.currency ?: vm.chartCurrency
    Card(Modifier.fillMaxWidth()) {
        SectionTitle("Tendencia del precio")
        if (closes.size < 30) {
            Muted("Calculando…", Modifier.padding(top = 6.dp))
        }
        val trends = listOf(126 to "Últimos 6 meses", 252 to "Últimos 12 meses")
            .mapNotNull { (n, label) -> Ind.trend(closes, n)?.let { Triple(n, label, it) } }
        for ((n, label, t) in trends) {
            val (col, arrow) = when (t.state) {
                Ind.TrendState.UP -> C.up to "▲"
                Ind.TrendState.DOWN -> C.down to "▼"
                Ind.TrendState.FLAT -> C.muted to "►"
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(C.border))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (closes.size < n) "$label (datos parciales)" else label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = C.text, modifier = Modifier.weight(1f))
                Tag("$arrow ${t.state.label}", col, col.copy(alpha = 0.10f))
            }
            TrendSpark(t, col, Modifier.padding(top = 6.dp).fillMaxWidth().height(40.dp))
            Row(Modifier.padding(top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Var. ${pct(t.changePct)}", fontSize = 11.sp, color = col, fontFamily = Mono)
                Text("Fuerza ${t.strength}", fontSize = 11.sp, color = C.text2)
                Text("Si sigue: ${fmtPrice(t.projection)}${curSuffix(currency)}", fontSize = 11.sp, color = C.text, fontFamily = Mono)
            }
        }
        Muted("Regresión lineal sobre cierres diarios. «Si sigue» extrapola la tendencia al mismo plazo; no es una predicción.", Modifier.padding(top = 8.dp), 10)
    }
}

@Composable
private fun SignalsCard(vm: AppViewModel) {
    val c = vm.candles
    Card(Modifier.fillMaxWidth()) {
        SectionTitle("Señales técnicas")
        Muted("Con velas ${vm.tf.desc}", size = 11)
        if (c.size < 30) {
            Muted("Datos insuficientes.", Modifier.padding(top = 6.dp))
        } else {
            SignalsContent(c)
        }
    }
}

@Composable
private fun SignalsContent(c: List<com.mitrading.app.data.Candle>) {
    run {
        val closes = c.map { it.c }
        val last = closes.last()
        val rsi = Ind.rsi(closes).last()
        val stoch = Ind.stochastic(c).first.last()
        val m50 = Ind.sma(closes, 50).last()
        val m200 = Ind.sma(closes, 200).last()
        val window = c.takeLast(90)
        val (res, sup) = Ind.levels(window, last)
        SignalRow("RSI (14)", rsi?.let { fmt(it, 1) } ?: "—", Ind.oscState(rsi, 70.0, 30.0), oscColor(Ind.oscState(rsi, 70.0, 30.0)))
        SignalRow("Estocástico (14,3)", stoch?.let { fmt(it, 1) } ?: "—", Ind.oscState(stoch, 80.0, 20.0), oscColor(Ind.oscState(stoch, 80.0, 20.0)))
        if (m50 != null) SignalRow("Precio vs MM50", fmtPrice(m50), if (last > m50) "Por encima" else "Por debajo", if (last > m50) C.up else C.down)
        if (m50 != null && m200 != null) SignalRow("MM50 vs MM200", fmtPrice(m200), if (m50 > m200) "Alcista" else "Bajista", if (m50 > m200) C.up else C.down)
        sup.firstOrNull()?.let { SignalRow("Soporte S1", fmtPrice(it), "−" + fmt((last - it) / last * 100, 1) + " %", C.sup) }
        res.firstOrNull()?.let { SignalRow("Resistencia R1", fmtPrice(it), "+" + fmt((it - last) / last * 100, 1) + " %", C.res) }
    }
}

private fun oscColor(state: String): Color = when (state) {
    "Sobrecompra" -> C.res
    "Sobreventa" -> C.sup
    else -> C.muted
}

@Composable
private fun SignalRow(name: String, value: String, verdict: String, col: Color) {
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(C.border))
    Row(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.sp, color = C.text2, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontFamily = Mono, color = C.text)
        Text(verdict, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = col, modifier = Modifier.width(92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun TargetCard(vm: AppViewModel) {
    val t = vm.target
    val r = vm.ratings
    val q = vm.quotes[vm.selected?.symbol]
    Card(Modifier.fillMaxWidth()) {
        SectionTitle("Precio objetivo de analistas")
        if (vm.store.fmpKey.isBlank()) {
            Muted("Añade tu clave gratuita de Financial Modeling Prep en Ajustes para ver el precio objetivo y el consenso.", Modifier.padding(top = 6.dp))
            Text("Ir a Ajustes", color = C.accent, fontSize = 13.sp, modifier = Modifier.clickable { vm.screen = Screen.SETTINGS }.padding(top = 8.dp))
        } else if (t == null) {
            Muted("No disponible para este valor con tu plan de datos (el plan gratuito solo cubre EE. UU.).", Modifier.padding(top = 6.dp))
        } else {
            val price = q?.price
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(fmtPrice(t.avg) + curSuffix(q?.currency ?: ""), fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                if (price != null && price > 0) {
                    val up = (t.avg - price) / price * 100
                    Spacer(Modifier.width(8.dp))
                    Text(pct(up) + " potencial", color = if (up >= 0) C.up else C.down, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            RangeBar(t.low, t.high, price, t.avg)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mín ${fmtPrice(t.low)}", fontSize = 10.sp, color = C.muted, fontFamily = Mono)
                Text("Media ${fmtPrice(t.avg)}", fontSize = 10.sp, color = C.accent, fontFamily = Mono)
                Text("Máx ${fmtPrice(t.high)}", fontSize = 10.sp, color = C.muted, fontFamily = Mono)
            }
        }
        if (r != null && r.total > 0) {
            val buy = r.strongBuy + r.buy
            val sell = r.sell + r.strongSell
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))) {
                if (buy > 0) Box(Modifier.weight(buy.toFloat()).fillMaxHeight().background(C.up))
                if (r.hold > 0) Box(Modifier.weight(r.hold.toFloat()).fillMaxHeight().background(C.muted))
                if (sell > 0) Box(Modifier.weight(sell.toFloat()).fillMaxHeight().background(C.down))
            }
            Row(Modifier.padding(top = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comprar $buy", fontSize = 11.sp, color = C.up, fontWeight = FontWeight.SemiBold)
                Text("Mantener ${r.hold}", fontSize = 11.sp, color = C.text2)
                Text("Vender $sell", fontSize = 11.sp, color = C.down, fontWeight = FontWeight.SemiBold)
            }
            if (r.consensus.isNotBlank()) Muted("Consenso: ${r.consensus}", Modifier.padding(top = 4.dp), 11)
        }
        Muted("Fuente: Financial Modeling Prep", Modifier.padding(top = 6.dp), 10)
    }
}

@Composable
private fun RangeBar(low: Double, high: Double, price: Double?, avg: Double) {
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    fun frac(v: Double) = ((v - low) / span).coerceIn(0.0, 1.0).toFloat()
    androidx.compose.foundation.Canvas(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(24.dp)) {
        val y = size.height / 2
        drawRoundRect(C.borderStrong, topLeft = androidx.compose.ui.geometry.Offset(0f, y - 3f), size = androidx.compose.ui.geometry.Size(size.width, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
        if (price != null) {
            val px = frac(price) * size.width
            drawLine(C.text, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), 4f)
        }
        val ax = frac(avg) * size.width
        drawCircle(Color.White, 10f, androidx.compose.ui.geometry.Offset(ax, y))
        drawCircle(C.accent, 7f, androidx.compose.ui.geometry.Offset(ax, y))
    }
}

@Composable
private fun ForecastNote(inst: Instrument) {
    Card(Modifier.fillMaxWidth()) {
        SectionTitle("Precio objetivo")
        Muted(
            "Los precios objetivo de analistas solo existen para acciones. Para ${inst.name} usa la tendencia, los soportes/resistencias y las noticias de materias primas y macro.",
            Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun MiniNews(n: NewsItem, onClick: () -> Unit) {
    val (fg, bg) = newsColors(n.cat)
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(C.surface)
            .border(1.dp, C.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(n.cat.label, fg, bg)
            Spacer(Modifier.width(6.dp))
            Muted(ago(n.time), size = 11)
        }
        Text(n.title, fontSize = 13.sp, color = C.text, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

fun newsColors(cat: com.mitrading.app.data.NewsCat): Pair<Color, Color> = when (cat) {
    com.mitrading.app.data.NewsCat.RESULTS -> C.purple to C.purple.copy(alpha = 0.10f)
    com.mitrading.app.data.NewsCat.ANALYSTS -> C.green2 to C.green2.copy(alpha = 0.10f)
    com.mitrading.app.data.NewsCat.COMMODITIES -> C.res to C.res.copy(alpha = 0.10f)
    com.mitrading.app.data.NewsCat.MACRO -> C.sup to C.sup.copy(alpha = 0.10f)
    com.mitrading.app.data.NewsCat.GENERAL -> C.text2 to C.chip
}
