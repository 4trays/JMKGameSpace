package com.luki.gamespace.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Dipakai buat berhenti polling & animasi pas app nggak kelihatan (hemat baterai, nggak panas). */
object AppState {
    var foreground by mutableStateOf(true)
}
