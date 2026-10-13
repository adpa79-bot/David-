package com.mitrading.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: AppViewModel) {
    Row(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
        Rail(vm)
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.screen) {
                Screen.MAIN -> MainScreen(vm)
                Screen.SEARCH -> SearchScreen(vm)
                Screen.PORTFOLIO -> PortfolioScreen(vm)
                Screen.ALERTS -> AlertsScreen(vm)
                Screen.NEWS -> NewsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun Rail(vm: AppViewModel) {
    Column(
        Modifier.width(80.dp).fillMaxHeight().background(C.rail).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(C.accent),
            contentAlignment = Alignment.Center
        ) { Icon(Ic.logo, contentDescription = "Mi trading", tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(10.dp))
        RailItem(vm, Screen.MAIN, Ic.star)
        RailItem(vm, Screen.SEARCH, Ic.search)
        RailItem(vm, Screen.PORTFOLIO, Ic.wallet)
        RailItem(vm, Screen.ALERTS, Ic.bell)
        RailItem(vm, Screen.NEWS, Ic.news)
        Spacer(Modifier.weight(1f))
        RailItem(vm, Screen.SETTINGS, Ic.settings)
    }
}

@Composable
private fun RailItem(vm: AppViewModel, screen: Screen, icon: ImageVector) {
    val active = vm.screen == screen
    Column(
        Modifier
            .size(width = 68.dp, height = 62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) C.chip else Color.Transparent)
            .clickable { vm.screen = screen },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (active) C.accent else C.muted, modifier = Modifier.size(23.dp))
        Text(
            screen.label, fontSize = 11.sp, color = if (active) C.accent else C.muted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp)
        )
    }
}
