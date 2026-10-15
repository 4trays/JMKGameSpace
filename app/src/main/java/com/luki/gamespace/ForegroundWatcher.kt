package com.luki.gamespace

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Deteksi app yang lagi di depan lewat Usage Access (polling ringan). */
class ForegroundWatcher(c: Context) {
    private val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var lastQuery = 0L
    private val ignore = setOf("com.android.systemui", "android")

    var current: String? = null
        private set

    @Suppress("DEPRECATION")
    fun poll(): String? {
        val now = System.currentTimeMillis()
        val begin = if (lastQuery == 0L) now - 2 * 3600_000L else lastQuery - 1000L
        lastQuery = now
        val ev = usm.queryEvents(begin, now) ?: return current
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val p = e.packageName
                if (p != null && p !in ignore) current = p
            }
        }
        return current
    }
}
