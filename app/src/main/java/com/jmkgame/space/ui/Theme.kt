package com.jmkgame.space.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Bg = Color(0xFF0A0A0D)

// Panel sedikit transparan kalau ada background custom, biar gambarnya tetap kelihatan.
val Panel: Color get() = if (CosmeticState.bg != null) Color(0xD915151A) else Color(0xFF15151A)
val Panel2: Color get() = if (CosmeticState.bg != null) Color(0xD91F1F27) else Color(0xFF1F1F27)

val Red: Color get() = CosmeticState.accent
val RedDark: Color get() = lerp(CosmeticState.accent, Color.Black, 0.55f)

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
