package com.luki.gamespace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0A0A0D)
val Panel = Color(0xFF15151A)
val Panel2 = Color(0xFF1F1F27)
val Red = Color(0xFFFF1F3D)
val RedDark = Color(0xFF8E0A1E)
val Dim = Color(0xFF9A9AA6)
val Green = Color(0xFF2EE59D)
val Amber = Color(0xFFFFB020)

@Composable
fun GameSpaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red,
            onPrimary = Color.White,
            background = Bg,
            surface = Panel,
            onSurface = Color.White,
            onBackground = Color.White
        ),
        content = content
    )
}
