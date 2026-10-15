package com.luki.gamespace

import android.app.Application
import android.os.Build

class GameSpaceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        if (!CrashLogger.isCrashProcess()) {
            CrashLogger.log(
                this, "I", "App",
                "Start | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            CrashLogger.logExitReasons(this)
        }
    }
}
