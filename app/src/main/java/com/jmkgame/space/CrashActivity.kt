package com.jmkgame.space

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jmkgame.space.ui.CrashScreen
import com.jmkgame.space.ui.GameSpaceTheme

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val trace = intent.getStringExtra("trace") ?: "(tidak ada stack trace)"
        setContent {
            GameSpaceTheme {
                CrashScreen(trace) {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }
}
