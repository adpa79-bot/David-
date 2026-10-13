package com.mitrading.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitrading.app.data.AssetType
import com.mitrading.app.data.Importer
import com.mitrading.app.data.Instrument

@Composable
fun PortfolioScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var broker by remember { mutableStateOf("XTB") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(context, uri, broker)
    }
    var mSymbol by remember { mutableStateOf("") }
    var mQty by remember { mutableStateOf("") }
    var mPrice by remember { mutableStateOf("") }
    var manualMsg by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize()) {
        // ---------------- Importar ----------------
        Column(
            Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            ScreenTitle("Mi cartera")
            Muted("Importa los extractos que descargas desde tus brokers. Los datos se quedan solo en esta tablet.", Modifier.padding(top = 4.dp))

            SectionTitle("1. Elige el broker", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("XTB", "Trade Republic", "Otro").forEach { b -> Pill(b, broker == b, { broker = b }) }
            }

            SectionTitle("2. Importa el archivo", Modifier.padding(top = 18.dp, bottom = 8.dp))
            PrimaryButton(
                if (vm.importing) "Importando…" else "Importar extracto (Excel o CSV)",
                {
                    picker.launch(
                        arrayOf(
                            "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "application/octet-stream"
                        )
                    )
                },
                Modifier.fillMaxWidth(), icon = Ic.upload, enabled = !vm.importing
            )
            vm.importMessage?.let { Muted(it, Modifier.padding(top = 8.dp), 13) }

            Card(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Text("¿Dónde descargo el extracto?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = C.text)
                Muted(
                    "XTB: en xStation, Historial de cuenta → Posiciones abiertas → Exportar (Excel).\n" +
                        "Trade Republic: en la web, Perfil → Actividad / Transacciones → Exportar (CSV), o el extracto de cartera.\n" +
                        "La app reconoce columnas de símbolo/ISIN, cantidad y precio, y suma compras y ventas.",
                    Modifier.padding(top = 4.dp)
                )
            }

            SectionTitle("O añade una posición a mano", Modifier.padding(top = 20.dp, bottom = 4.dp))
            OutlinedTextField(mSymbol, { mSymbol = it }, Modifier.fillMaxWidth(), label = { Text("Símbolo o ISIN (p. ej. IBE.MC, AAPL)") }, singleLine = true)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    mQty, { mQty = it }, Modifier.weight(1f), label = { Text("Cantidad") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    mPrice, { mPrice = it }, Modifier.weight(1f), label = { Text("Precio medio") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            SecondaryButton("Añadir posición", {
                val qty = Importer.parseNumber(mQty)
                val price = Importer.parseNumber(mPrice)
                manualMsg = if (mSymbol.isBlank() || qty == null || price == null) "Rellena símbolo, cantidad y precio."
                else {
                    vm.addPosition(mSymbol.trim(), qty, price, broker)
                    mSymbol = ""; mQty = ""; mPrice = ""
                    "Posición añadida."
                }
            }, Modifier.padding(top = 8.dp).fillMaxWidth(), icon = Ic.plus)
            manualMsg?.let { Muted(it, Modifier.padding(top = 6.dp)) }

            SectionTitle("Operar", Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Abrir XTB", { vm.openBroker(context, "XTB") }, icon = Ic.external)
                SecondaryButton("Abrir Trade Republic", { vm.openBroker(context, "Trade Republic") }, icon = Ic.external)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))

        // ---------------- Posiciones ----------------
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 20.dp)) {
            var totalValue = 0.0
            var totalCost = 0.0
            vm.positions.forEach { p ->
                val q = vm.quotes[p.symbol]
                if (q != null) { totalValue += q.price * p.qty; totalCost += p.avgPrice * p.qty }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Posiciones")
                Spacer(Modifier.width(12.dp))
                Muted("${vm.positions.size} valores", Modifier.weight(1f))
                if (vm.positions.isNotEmpty()) SecondaryButton("Añadir todas a favoritos", { vm.addPositionsToFavorites() }, icon = Ic.star)
            }
            if (totalCost > 0) {
                val pl = totalValue - totalCost
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column { Muted("Valor actual"); Text(fmt(totalValue), fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.text) }
                    Column { Muted("Coste"); Text(fmt(totalCost), fontFamily = Mono, fontSize = 20.sp, color = C.text2) }
                    Column {
                        Muted("Ganancia / pérdida")
                        Text("${if (pl >= 0) "+" else ""}${fmt(pl)} (${pct(pl / totalCost * 100)})", fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (pl >= 0) C.up else C.down)
                    }
                }
                Muted("Totales sumados en la divisa de cada valor (sin conversión de moneda).", Modifier.padding(top = 4.dp), 11)
            }
            Row(Modifier.padding(top = 16.dp, bottom = 6.dp)) {
                listOf("Valor" to 2.2f, "Broker" to 1f, "Cantidad" to 1f, "P. medio" to 1f, "P. actual" to 1f, "G/P" to 1.3f).forEach { (h, w) ->
                    Text(h.uppercase(), fontSize = 11.sp, color = C.muted, modifier = Modifier.weight(w), textAlign = if (h == "Valor" || h == "Broker") TextAlign.Start else TextAlign.End)
                }
                Spacer(Modifier.width(48.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
            if (vm.positions.isEmpty()) Muted("Sin posiciones todavía. Importa un extracto o añade una a mano.", Modifier.padding(top = 14.dp), 14)
            LazyColumn(Modifier.weight(1f)) {
                items(vm.positions.toList()) { p ->
                    val q = vm.quotes[p.symbol]
                    val resolved = !p.symbol.startsWith("ISIN:") && !p.symbol.startsWith("NAME:")
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(enabled = resolved) { vm.open(Instrument(p.symbol, p.name, AssetType.STOCK)) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(2.2f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(if (resolved) p.symbol else "${p.symbol} (no encontrado)", size = 11)
                        }
                        Text(p.broker, fontSize = 13.sp, color = C.text2, modifier = Modifier.weight(1f))
                        Text(fmt(p.qty, if (p.qty % 1.0 == 0.0) 0 else 4), fontFamily = Mono, fontSize = 13.sp, color = C.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(fmtPrice(p.avgPrice), fontFamily = Mono, fontSize = 13.sp, color = C.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(q?.let { fmtPrice(it.price) } ?: "—", fontFamily = Mono, fontSize = 13.sp, color = C.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.End) {
                            if (q != null && p.avgPrice > 0) {
                                val pl = (q.price - p.avgPrice) * p.qty
                                val plPct = (q.price - p.avgPrice) / p.avgPrice * 100
                                val col = if (pl >= 0) C.up else C.down
                                Text((if (pl >= 0) "+" else "") + fmt(pl) + curSuffix(q.currency), fontFamily = Mono, fontSize = 13.sp, color = col, fontWeight = FontWeight.SemiBold)
                                Text(pct(plPct), fontFamily = Mono, fontSize = 11.sp, color = col)
                            } else Text("—", color = C.muted)
                        }
                        IconButton(onClick = { vm.removePosition(p) }) {
                            Icon(Ic.trash, contentDescription = "Eliminar posición", tint = C.muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
                }
            }
            if (vm.importing) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = C.accent)
        }
    }
}
