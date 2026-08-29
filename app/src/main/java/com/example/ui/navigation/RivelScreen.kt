package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector

enum class RivelScreen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    ALARMS("alarms", "Alarms", Icons.Filled.Alarm, Icons.Outlined.Alarm),
    STUDY("study", "Study Plan", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    FOCUS("focus", "Focus", Icons.Filled.Timer, Icons.Outlined.Timer),
    AI("ai", "RIVEL AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
}
