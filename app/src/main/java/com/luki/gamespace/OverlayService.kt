package com.luki.gamespace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Otak Smart Sidebar:
 * - cek app yang lagi di depan (Usage Access)
 * - kalau itu game yang di-add -> tampilkan handle sidebar (+ HUD kalau aktif), auto-boost
 * - kalau keluar dari game -> sembunyikan, lalu restore boost setelah jeda singkat
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var watcher: ForegroundWatcher
    private val handler = Handler(Looper.getMainLooper())

    private var sidebar: Sidebar? = null
    private var hud: TextView? = null
    private var inGame = false
    private var leaveSince = 0L
    private var autoBusy = false
    private var noAccessLogged = false

    private val tick = object : Runnable {
        override fun run() {
            val next = try {
                onTick()
            } catch (t: Throwable) {
                CrashLogger.log(this@OverlayService, "E", "Overlay", "Tick error", t)
                3000L
            }
            handler.postDelayed(this, next)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        watcher = ForegroundWatcher(this)
        CrashLogger.log(this, "I", "Overlay", "Service dibuat")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFully()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
        } catch (t: Throwable) {
            CrashLogger.log(this, "E", "Overlay", "startForeground gagal", t)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Prefs.sidebar(this) && !Prefs.overlay(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun stopFully() {
        Prefs.setSidebar(this, false)
        Prefs.setOverlay(this, false)
        BoostManager.deactivate(this)
        hideUi()
        stopSelf()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Game Space", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Game Space aktif")
            .setContentText("Smart Sidebar siap di game kamu")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_bolt), "Matikan", stopPi).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ───────────────────────── TICK ─────────────────────────

    /** return: delay ke tick berikutnya (ms) */
    private fun onTick(): Long {
        if (!Prefs.sidebar(this) && !Prefs.overlay(this)) {
            hideUi()
            stopSelf()
            return 5000L
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            hideUi()
            return 4000L
        }
        if (!Settings.canDrawOverlays(this) || !BoostManager.hasUsageAccess(this)) {
            if (!noAccessLogged) {
                CrashLogger.log(this, "W", "Overlay", "Izin overlay / usage access belum lengkap")
                noAccessLogged = true
            }
            hideUi()
            return 3000L
        }
        noAccessLogged = false

        val fg = watcher.poll()
        val games = GameRepo.savedPkgs(this)
        val nowInGame = fg != null && fg in games
        val now = SystemClock.elapsedRealtime()

        if (nowInGame && fg != null) {
            leaveSince = 0L
            if (!inGame) {
                inGame = true
                onEnterGame(fg)
            }
            ensureUi(fg)
            val s = BoostManager.stats(this)
            updateUi(s)
            return if (sidebar?.isOpen == true) 1000L else 1500L
        }

        hideUi()
        if (inGame) {
            if (leaveSince == 0L) leaveSince = now
            if (now - leaveSince >= LEAVE_GRACE_MS) {
                inGame = false
                leaveSince = 0L
                onLeaveGame()
            }
        }
        return 1500L
    }

    private fun onEnterGame(pkg: String) {
        CrashLogger.log(this, "I", "Overlay", "Masuk game: $pkg")
        if (Prefs.autoBoost(this) && !BoostManager.isActive(this) && !autoBusy) {
            autoBusy = true
            val mode = Prefs.mode(this)
            thread {
                try {
                    BoostManager.activate(this, mode, true, setOf(pkg))
                } finally {
                    autoBusy = false
                }
            }
        }
    }

    private fun onLeaveGame() {
        CrashLogger.log(this, "I", "Overlay", "Keluar dari game")
        if (BoostManager.isActive(this) && BoostManager.isAuto(this)) {
            thread { BoostManager.deactivate(this) }
        }
    }

    // ───────────────────────── UI ─────────────────────────

    private fun tickNow() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun ensureUi(pkg: String) {
        if (Prefs.sidebar(this)) {
            val existing = sidebar
            if (existing != null && existing.side != Prefs.side(this)) {
                existing.destroy()
                sidebar = null
            }
            val sb = sidebar ?: Sidebar(this, wm, { stopFully() }, { tickNow() }).also {
                sidebar = it
                it.showHandle()
            }
            sb.gamePkg = pkg
            sb.clampHandle()
        } else {
            sidebar?.destroy()
            sidebar = null
        }
        if (Prefs.overlay(this)) showHud() else hideHud()
    }

    private fun updateUi(s: Stats) {
        val hz = runCatching {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY).refreshRate.toInt()
        }.getOrDefault(60)
        sidebar?.refresh(s, hz)
        hud?.let {
            val t = "TEMP %.1f°C  RAM %d%%\nBATT %d%%%s  %dHz".format(s.tempC, s.ramPct, s.batt, if (s.charging) "+" else "", hz)
            if (it.text.toString() != t) it.text = t
        }
    }

    private fun hideUi() {
        sidebar?.destroy()
        sidebar = null
        hideHud()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showHud() {
        if (hud != null) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(60)
            y = dp(12)
        }
        val view = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(0xCC0A0A0D.toInt())
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xFFFF1F3D.toInt())
            }
            text = "..."
        }
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - tx).toInt()
                    lp.y = sy + (e.rawY - ty).toInt()
                    runCatching { wm.updateViewLayout(view, lp) }
                }
            }
            true
        }
        runCatching { wm.addView(view, lp) }.onSuccess { hud = view }
    }

    private fun hideHud() {
        hud?.let { v -> runCatching { wm.removeView(v) } }
        hud = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideUi()
        running = false
        CrashLogger.log(this, "I", "Overlay", "Service dimatikan")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "jmk48game.space.STOP"
        private const val CHANNEL = "gamespace_boost"
        private const val NOTIF_ID = 1001
        private const val LEAVE_GRACE_MS = 8000L

        @Volatile
        var running = false
    }
}
