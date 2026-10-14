package com.luki.gamespace

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class Stats(
    val ramPct: Int = 0,
    val ramFreeMb: Long = 0,
    val batt: Int = 0,
    val tempC: Float = 0f,
    val charging: Boolean = false
)

data class Perms(
    val overlay: Boolean = false,
    val dnd: Boolean = false,
    val write: Boolean = false,
    val notif: Boolean = false
)

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun mode(c: Context): BoostManager.Mode =
        runCatching { BoostManager.Mode.valueOf(p(c).getString("mode", "BALANCED")!!) }
            .getOrDefault(BoostManager.Mode.BALANCED)
    fun setMode(c: Context, m: BoostManager.Mode) { p(c).edit().putString("mode", m.name).apply() }
    fun overlay(c: Context) = p(c).getBoolean("overlay", true)
    fun setOverlay(c: Context, v: Boolean) { p(c).edit().putBoolean("overlay", v).apply() }
}

object BoostManager {
    enum class Mode(val label: String, val desc: String) {
        STANDARD("Standard", "Tanpa tweak. Cuma jalanin game + overlay."),
        BALANCED("Balanced", "Bersihin RAM background + mode jangan ganggu (DND) pas main."),
        EXTREME("Extreme", "Balanced + brightness layar dinaikkin ke max. Boros baterai & panas.")
    }

    private fun sp(c: Context) = c.getSharedPreferences("boost_state", Context.MODE_PRIVATE)
    private const val TAG = "Boost"

    fun isActive(c: Context) = sp(c).getBoolean("active", false)

    fun perms(c: Context): Perms {
        val nm = c.getSystemService(NotificationManager::class.java)
        val notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return Perms(
            overlay = Settings.canDrawOverlays(c),
            dnd = nm.isNotificationPolicyAccessGranted,
            write = Settings.System.canWrite(c),
            notif = notif
        )
    }

    fun stats(c: Context): Stats {
        val am = c.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val used = if (mi.totalMem > 0) (100 - mi.availMem * 100 / mi.totalMem).toInt() else 0
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Stats(
            ramPct = used,
            ramFreeMb = mi.availMem / (1024 * 1024),
            batt = if (scale > 0) lvl * 100 / scale else lvl,
            tempC = temp,
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    /** returns (jumlah app ditutup, MB RAM yang bertambah) */
    fun cleanRam(c: Context, exclude: Set<String>): Pair<Int, Long> {
        return try {
            val am = c.getSystemService(ActivityManager::class.java)
            val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val pkgs = c.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != c.packageName && it !in exclude }
            var n = 0
            pkgs.forEach { pkg -> runCatching { am.killBackgroundProcesses(pkg); n++ } }
            Thread.sleep(400)
            val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val freed = ((after - before) / (1024 * 1024)).coerceAtLeast(0)
            CrashLogger.log(c, "I", TAG, "Clean RAM: $n app dikirim kill, +$freed MB")
            Pair(n, freed)
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Clean RAM gagal", t)
            Pair(0, 0L)
        }
    }

    fun activate(c: Context, mode: Mode, overlay: Boolean, exclude: Set<String>) {
        try {
            val sp = sp(c)
            val first = !sp.getBoolean("active", false)
            CrashLogger.log(c, "I", TAG, "Activate mode=${mode.name} overlay=$overlay")

            if (mode != Mode.STANDARD) {
                cleanRam(c, exclude)
                runCatching {
                    val nm = c.getSystemService(NotificationManager::class.java)
                    if (nm.isNotificationPolicyAccessGranted) {
                        if (first) sp.edit().putInt("prev_dnd", nm.currentInterruptionFilter).putBoolean("dnd_changed", true).commit()
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        CrashLogger.log(c, "I", TAG, "DND aktif")
                    } else {
                        CrashLogger.log(c, "W", TAG, "Izin DND belum diberikan, dilewati")
                    }
                }.onFailure { CrashLogger.log(c, "E", TAG, "DND gagal", it) }
            }

            if (mode == Mode.EXTREME) {
                runCatching {
                    if (Settings.System.canWrite(c)) {
                        val cr = c.contentResolver
                        if (first) {
                            sp.edit()
                                .putInt("prev_br", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                                .putInt("prev_br_mode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0))
                                .putBoolean("br_changed", true)
                                .commit()
                        }
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 255)
                        CrashLogger.log(c, "I", TAG, "Brightness max")
                    } else {
                        CrashLogger.log(c, "W", TAG, "Izin WRITE_SETTINGS belum diberikan, brightness dilewati")
                    }
                }.onFailure { CrashLogger.log(c, "E", TAG, "Brightness gagal", it) }
            }

            sp.edit().putBoolean("active", true).commit()
            if (overlay) startOverlay(c)
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Activate gagal", t)
        }
    }

    fun deactivate(c: Context) {
        try {
            val sp = sp(c)
            CrashLogger.log(c, "I", TAG, "Deactivate")
            if (sp.getBoolean("dnd_changed", false)) {
                runCatching {
                    val nm = c.getSystemService(NotificationManager::class.java)
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(sp.getInt("prev_dnd", NotificationManager.INTERRUPTION_FILTER_ALL))
                    }
                }.onFailure { CrashLogger.log(c, "E", TAG, "Restore DND gagal", it) }
            }
            if (sp.getBoolean("br_changed", false)) {
                runCatching {
                    if (Settings.System.canWrite(c)) {
                        val cr = c.contentResolver
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("prev_br_mode", 0))
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("prev_br", 128))
                    }
                }.onFailure { CrashLogger.log(c, "E", TAG, "Restore brightness gagal", it) }
            }
            sp.edit().putBoolean("active", false).putBoolean("dnd_changed", false).putBoolean("br_changed", false).commit()
            stopOverlay(c)
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Deactivate gagal", t)
        }
    }

    fun startOverlay(c: Context) {
        runCatching {
            if (!Settings.canDrawOverlays(c)) {
                CrashLogger.log(c, "W", TAG, "Izin overlay belum diberikan, overlay dilewati")
                return
            }
            ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java))
        }.onFailure { CrashLogger.log(c, "E", TAG, "Start overlay gagal", it) }
    }

    fun stopOverlay(c: Context) {
        runCatching { c.stopService(Intent(c, OverlayService::class.java)) }
    }

    fun launchGame(c: Context, pkg: String): Boolean {
        return try {
            val i = c.packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) {
                CrashLogger.log(c, "E", TAG, "Launch intent tidak ada untuk $pkg")
                false
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(i)
                CrashLogger.log(c, "I", TAG, "Launch $pkg")
                true
            }
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Launch gagal $pkg", t)
            false
        }
    }
}
