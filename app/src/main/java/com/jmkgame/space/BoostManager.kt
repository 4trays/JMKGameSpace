package com.jmkgame.space

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
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
    val usage: Boolean = false,
    val dnd: Boolean = false,
    val write: Boolean = false,
    val notif: Boolean = false,
    val battery: Boolean = false
)

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun mode(c: Context): BoostManager.Mode =
        runCatching { BoostManager.Mode.valueOf(p(c).getString("mode", "BALANCED")!!) }
            .getOrDefault(BoostManager.Mode.BALANCED)
    fun setMode(c: Context, m: BoostManager.Mode) { p(c).edit().putString("mode", m.name).apply() }

    /** HUD teks kecil di atas game */
    fun overlay(c: Context) = p(c).getBoolean("overlay", false)
    fun setOverlay(c: Context, v: Boolean) { p(c).edit().putBoolean("overlay", v).apply() }

    fun sidebar(c: Context) = p(c).getBoolean("sidebar", true)
    fun setSidebar(c: Context, v: Boolean) { p(c).edit().putBoolean("sidebar", v).apply() }

    fun autoBoost(c: Context) = p(c).getBoolean("auto_boost", true)
    fun setAutoBoost(c: Context, v: Boolean) { p(c).edit().putBoolean("auto_boost", v).apply() }

    /** "L" atau "R" */
    fun side(c: Context) = p(c).getString("side", "L") ?: "L"
    fun setSide(c: Context, v: String) { p(c).edit().putString("side", v).apply() }

    fun accent(c: Context) = p(c).getInt("accent", 0xFFFF1F3D.toInt())
    fun setAccent(c: Context, v: Int) { p(c).edit().putInt("accent", v).apply() }

    fun bgDim(c: Context) = p(c).getFloat("bg_dim", 0.6f)
    fun setBgDim(c: Context, v: Float) { p(c).edit().putFloat("bg_dim", v).apply() }

    fun fps(c: Context) = p(c).getBoolean("fps", true)
    fun setFps(c: Context, v: Boolean) { p(c).edit().putBoolean("fps", v).apply() }

    fun handleY(c: Context) = p(c).getFloat("handle_y", 0.35f)
    fun setHandleY(c: Context, v: Float) { p(c).edit().putFloat("handle_y", v).apply() }
}

object BoostManager {
    enum class Mode(val label: String, val desc: String) {
        STANDARD("Standard", "Tanpa tweak sistem. Cuma sidebar & monitor."),
        BALANCED("Balanced", "Bersihin RAM background + mode jangan ganggu (DND) pas main."),
        EXTREME("Extreme", "Balanced + brightness layar dinaikkin ke max. Boros baterai & panas.")
    }

    private fun sp(c: Context) = c.getSharedPreferences("boost_state", Context.MODE_PRIVATE)
    private const val TAG = "Boost"

    fun isActive(c: Context) = sp(c).getBoolean("active", false)
    fun isAuto(c: Context) = sp(c).getBoolean("auto", false)

    fun hasUsageAccess(c: Context): Boolean = runCatching {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val m = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        m == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun perms(c: Context): Perms {
        val nm = c.getSystemService(NotificationManager::class.java)
        val notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val pm = c.getSystemService(PowerManager::class.java)
        return Perms(
            overlay = Settings.canDrawOverlays(c),
            usage = hasUsageAccess(c),
            dnd = nm.isNotificationPolicyAccessGranted,
            write = Settings.System.canWrite(c),
            notif = notif,
            battery = pm.isIgnoringBatteryOptimizations(c.packageName)
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

    /** returns (jumlah app dikirim kill, MB RAM yang bertambah) */
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

    // ---------- DND ----------
    fun isDndOn(c: Context): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return false
        val f = nm.currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** true=sekarang ON, false=OFF, null=izin belum ada */
    fun toggleDnd(c: Context): Boolean? {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return null
        val on = isDndOn(c)
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
        return !on
    }

    private fun enableDnd(c: Context) {
        runCatching {
            val sp = sp(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                if (!sp.getBoolean("dnd_changed", false)) {
                    sp.edit().putInt("prev_dnd", nm.currentInterruptionFilter).putBoolean("dnd_changed", true).commit()
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                CrashLogger.log(c, "I", TAG, "DND aktif")
            } else {
                CrashLogger.log(c, "W", TAG, "Izin DND belum diberikan, dilewati")
            }
        }.onFailure { CrashLogger.log(c, "E", TAG, "DND gagal", it) }
    }

    private fun restoreDnd(c: Context) {
        runCatching {
            val sp = sp(c)
            if (sp.getBoolean("dnd_changed", false)) {
                val nm = c.getSystemService(NotificationManager::class.java)
                if (nm.isNotificationPolicyAccessGranted) {
                    val prev = sp.getInt("prev_dnd", NotificationManager.INTERRUPTION_FILTER_ALL)
                    nm.setInterruptionFilter(if (prev == 0) NotificationManager.INTERRUPTION_FILTER_ALL else prev)
                }
                sp.edit().putBoolean("dnd_changed", false).commit()
            }
        }.onFailure { CrashLogger.log(c, "E", TAG, "Restore DND gagal", it) }
    }

    // ---------- Brightness ----------
    private fun enableBrightMax(c: Context) {
        runCatching {
            if (Settings.System.canWrite(c)) {
                val sp = sp(c)
                val cr = c.contentResolver
                if (!sp.getBoolean("br_changed", false)) {
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

    private fun restoreBrightness(c: Context) {
        runCatching {
            val sp = sp(c)
            if (sp.getBoolean("br_changed", false)) {
                if (Settings.System.canWrite(c)) {
                    val cr = c.contentResolver
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("prev_br_mode", 0))
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("prev_br", 128))
                }
                sp.edit().putBoolean("br_changed", false).commit()
            }
        }.onFailure { CrashLogger.log(c, "E", TAG, "Restore brightness gagal", it) }
    }

    /**
     * auto=true  -> boost ikut dimatikan otomatis pas keluar dari game (kalau service sidebar jalan)
     * auto=false -> boost manual, dimatikan manual
     */
    @Synchronized
    fun activate(c: Context, mode: Mode, auto: Boolean, exclude: Set<String>) {
        try {
            CrashLogger.log(c, "I", TAG, "Activate mode=${mode.name} auto=$auto")
            if (mode != Mode.STANDARD) {
                cleanRam(c, exclude)
                enableDnd(c)
            } else {
                restoreDnd(c)
            }
            if (mode == Mode.EXTREME) enableBrightMax(c) else restoreBrightness(c)
            sp(c).edit().putBoolean("active", true).putBoolean("auto", auto).commit()
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Activate gagal", t)
        }
    }

    @Synchronized
    fun deactivate(c: Context) {
        try {
            CrashLogger.log(c, "I", TAG, "Deactivate")
            restoreDnd(c)
            restoreBrightness(c)
            sp(c).edit().putBoolean("active", false).putBoolean("auto", false).commit()
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", TAG, "Deactivate gagal", t)
        }
    }

    // ---------- Service ----------
    fun serviceWanted(c: Context): Boolean =
        (Prefs.sidebar(c) || Prefs.overlay(c)) && Settings.canDrawOverlays(c) && hasUsageAccess(c)

    /** Dipanggil dari UI (foreground) tiap detik: nyalain/matiin service sesuai pengaturan. */
    fun syncService(c: Context) {
        val enabled = Prefs.sidebar(c) || Prefs.overlay(c)
        if (serviceWanted(c) && !OverlayService.running) startOverlay(c)
        else if (!enabled && OverlayService.running) stopOverlay(c)
    }

    fun startOverlay(c: Context) {
        runCatching {
            ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java))
        }.onFailure { CrashLogger.log(c, "E", TAG, "Start service gagal", it) }
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
