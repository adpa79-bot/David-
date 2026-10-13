package com.mitrading.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val uri = LocalUriHandler.current
    var saved by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f).widthIn(max = 620.dp)) {
            ScreenTitle("Ajustes · Fuentes de datos")
            Muted("Las claves se guardan solo en esta tablet.", Modifier.padding(top = 4.dp))

            Card(Modifier.padding(top = 18.dp).fillMaxWidth()) {
                Text("Twelve Data · cotizaciones y gráficos", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
                Muted(
                    "Plan gratuito: acciones de EE. UU. en tiempo real (800 consultas/día). Planes Pro: tiempo real europeo. " +
                        "Sin clave, la app usa Yahoo Finance (Europa con ~15 min de retraso). Índices y futuros siempre llegan de Yahoo.",
                    Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = vm.twelveKey, onValueChange = { vm.twelveKey = it; saved = false },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), singleLine = true,
                    label = { Text("Clave API de Twelve Data") }
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = vm.useTwelve, onCheckedChange = { vm.useTwelve = it; saved = false },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = Color.White)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Usar Twelve Data cuando haya clave", fontSize = 13.sp, color = C.text2, modifier = Modifier.weight(1f))
                    SecondaryButton("Probar", { vm.testTwelve() })
                }
                vm.twelveTest?.let { Muted(it, Modifier.padding(top = 6.dp), 13) }
                Text(
                    "Conseguir clave gratuita → twelvedata.com", color = C.accent, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).clickableText { runCatching { uri.openUri("https://twelvedata.com/register") } }
                )
            }

            Card(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Text("Financial Modeling Prep · analistas, noticias y resultados", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
                Muted(
                    "Precio objetivo y consenso de analistas, noticias de empresas y calendario de resultados. " +
                        "El plan gratuito cubre acciones de EE. UU.; para empresas europeas hace falta un plan con cobertura global.",
                    Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = vm.fmpKey, onValueChange = { vm.fmpKey = it; saved = false },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), singleLine = true,
                    label = { Text("Clave API de Financial Modeling Prep") }
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("Probar", { vm.testFmp() })
                }
                vm.fmpTest?.let { Muted(it, Modifier.padding(top = 6.dp), 13) }
                Text(
                    "Conseguir clave gratuita → financialmodelingprep.com", color = C.accent, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).clickableText { runCatching { uri.openUri("https://site.financialmodelingprep.com/register") } }
                )
            }

            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Guardar", { vm.saveKeys(); saved = true })
                Spacer(Modifier.width(12.dp))
                if (saved) Text("Guardado ✓", color = C.up, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.width(360.dp)) {
            SectionTitle("Cómo obtiene los datos la app")
            Muted(
                "• Precios y gráficos: Twelve Data si has puesto clave; si no, o si un valor no está en tu plan, Yahoo Finance.\n\n" +
                    "• Índices (equivalentes a los CFD de XTB) y futuros: Yahoo Finance. El precio de un CFD lo fija tu broker, pero sigue al índice casi exactamente.\n\n" +
                    "• Precio objetivo, consenso, noticias de empresas y resultados: Financial Modeling Prep. Sin clave, las noticias llegan de Yahoo Finance.\n\n" +
                    "• Alertas: se comprueban cada ~15 minutos aunque la app esté cerrada (límite de Android).\n\n" +
                    "• La app no accede a tus cuentas de XTB ni de Trade Republic: Android no lo permite y ninguno de los dos ofrece API pública. Usa la pantalla Cartera para importar tus extractos.",
                Modifier.padding(top = 8.dp), 13
            )
            SectionTitle("Aviso", Modifier.padding(top = 18.dp))
            Muted(
                "Mi trading es una herramienta de análisis personal. Las señales, tendencias y precios objetivo son informativos y no constituyen asesoramiento financiero.",
                Modifier.padding(top = 6.dp), 13
            )
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick).padding(vertical = 4.dp)
