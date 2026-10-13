package com.mitrading.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitrading.app.data.NewsCat

@Composable
fun NewsScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    var cat by remember { mutableStateOf<NewsCat?>(null) }
    LaunchedEffect(Unit) { vm.loadNews() }
    val names = vm.favorites.associate { it.symbol.uppercase() to it.name }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Noticias")
                Spacer(Modifier.width(12.dp))
                Muted("De tus favoritos · ${vm.news.size} noticias", Modifier.weight(1f))
                if (vm.newsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = C.accent)
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Actualizar", { vm.loadNews(force = true) }, icon = Ic.refresh)
            }
            Row(
                Modifier.padding(vertical = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Todas", cat == null, { cat = null }, round = true)
                NewsCat.entries.forEach { c -> Pill(c.label, cat == c, { cat = c }, round = true) }
            }
            val list = vm.news.filter { cat == null || it.cat == cat }
            if (list.isEmpty() && !vm.newsLoading) Muted("No hay noticias en esta categoría.", size = 14)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(list, key = { it.url + it.title }) { n ->
                    val (fg, bg) = newsColors(n.cat)
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .clip(shape)
                            .background(C.surface)
                            .border(1.dp, C.border, shape)
                            .clickable { if (n.url.isNotBlank()) runCatching { uri.openUri(n.url) } }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(n.cat.label, fg, bg)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                names[n.symbol.uppercase()] ?: n.symbol, fontSize = 12.sp, color = C.text2,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Muted(ago(n.time), size = 11)
                        }
                        Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.text, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (n.summary.isNotBlank()) {
                            Text(n.summary, fontSize = 13.sp, color = C.text2, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Muted("Fuente: ${n.publisher.ifBlank { "—" }}", Modifier.padding(top = 10.dp), 11)
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("Próximos resultados (60 días)")
            if (vm.store.fmpKey.isBlank()) {
                Muted("Añade tu clave de Financial Modeling Prep en Ajustes para ver el calendario de resultados de tus acciones.", Modifier.padding(top = 6.dp))
                Text("Ir a Ajustes", color = C.accent, fontSize = 13.sp, modifier = Modifier.clickable { vm.screen = Screen.SETTINGS }.padding(top = 8.dp))
            } else if (vm.earnings.isEmpty()) {
                Muted(if (vm.newsLoading) "Cargando…" else "Ninguna de tus acciones publica resultados en los próximos 60 días (o no está cubierta por tu plan).", Modifier.padding(top = 6.dp))
            }
            vm.earnings.forEach { e ->
                Card(Modifier.padding(top = 8.dp).fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.date, fontFamily = Mono, fontSize = 12.sp, color = C.purple, modifier = Modifier.width(92.dp))
                        Column {
                            Text(names[e.symbol.uppercase()] ?: e.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.text)
                            Muted("BPA estimado: ${e.epsEstimated?.let { fmt(it) } ?: "—"}", size = 11)
                        }
                    }
                }
            }
            SectionTitle("Cómo se clasifican", Modifier.padding(top = 20.dp))
            Muted(
                "Resultados: informes trimestrales y beneficios. Analistas: cambios de recomendación y precio objetivo. " +
                    "Materias primas: producción, OPEP, clima y cosechas. Macro: inflación, tipos de interés y bancos centrales.",
                Modifier.padding(top = 6.dp)
            )
        }
    }
}
