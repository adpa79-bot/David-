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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitrading.app.data.Instrument
import com.mitrading.app.data.Store

@Composable
fun SearchScreen(vm: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 20.dp)) {
            ScreenTitle("Buscar instrumento")
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.search(it) },
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Nombre, ticker o ISIN (p. ej. Iberdrola, AAPL, ES0144580Y14)") },
                leadingIcon = { Icon(Ic.search, contentDescription = null, tint = C.muted) },
                shape = RoundedCornerShape(12.dp)
            )
            Muted(
                "Acciones de EE. UU. y Europa, índices, futuros y ETF/ETC de materias primas.",
                Modifier.padding(top = 8.dp)
            )
            Row(Modifier.padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.accent)
                Muted(
                    when {
                        vm.searching -> "  Buscando…"
                        vm.searchError != null -> vm.searchError ?: ""
                        vm.searchResults.isNotEmpty() -> "${vm.searchResults.size} resultados · toca la estrella para añadir a favoritos"
                        else -> "Escribe para buscar."
                    }
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(vm.searchResults, key = { it.symbol }) { inst -> ResultCard(vm, inst) }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        Column(
            Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            SectionTitle("Índices (equivalentes a los CFD), futuros y materias primas")
            Muted("Toca la estrella para añadir; toca el nombre para ver el gráfico.", Modifier.padding(top = 4.dp, bottom = 8.dp))
            Store.QUICK_MARKETS.forEach { inst ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).clickable { vm.open(inst) }.padding(vertical = 6.dp)) {
                        Text(inst.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.text)
                        Muted("${inst.symbol} · ${inst.type.label}", size = 11)
                    }
                    StarButton(vm, inst)
                }
            }
            Muted(
                "Consejo: desde la app de XTB o Trade Republic puedes usar «Compartir» en un valor y elegir Mi trading para buscarlo aquí.",
                Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, inst: Instrument) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(C.surface)
            .border(1.dp, C.border, shape)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).clickable { vm.open(inst) }) {
            Text(inst.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${inst.type.label} · ${inst.symbol}${if (inst.exchange.isNotBlank()) " · " + inst.exchange else ""}",
                fontSize = 11.sp, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        StarButton(vm, inst)
    }
}

@Composable
fun StarButton(vm: AppViewModel, inst: Instrument) {
    val fav = vm.isFavorite(inst.symbol)
    IconButton(onClick = { vm.toggleFavorite(inst) }) {
        Icon(
            if (fav) Ic.star else Ic.starOutline,
            contentDescription = if (fav) "Quitar de favoritos" else "Añadir a favoritos",
            tint = if (fav) C.star else C.muted
        )
    }
}
