package com.luki.gamespace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luki.gamespace.ui.AppRoot
import com.luki.gamespace.ui.GameSpaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GameSpaceTheme { AppRoot() } }
    }
}
