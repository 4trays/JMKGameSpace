package com.jmkgame.space

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jmkgame.space.ui.AppRoot
import com.jmkgame.space.ui.AppState
import com.jmkgame.space.ui.CosmeticState
import com.jmkgame.space.ui.GameSpaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CosmeticState.loadPrefs(this)
        setContent { GameSpaceTheme { AppRoot() } }
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        AppState.foreground = false
        super.onStop()
    }
}
