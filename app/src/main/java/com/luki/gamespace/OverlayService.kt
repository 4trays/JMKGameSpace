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
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var tv: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BoostManager.deactivate(this)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
            if (tv == null) addOverlay()
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (t: Throwable) {
            CrashLogger.log(this, "E", "Overlay", "Service gagal start", t)
            stopSelf()
        }
        return START_STICKY
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
            .setContentText("Boost & overlay berjalan")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_bolt), "Matikan Boost", stopPi).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            CrashLogger.log(this, "W", "Overlay", "Izin overlay tidak ada")
            return
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(80)
        }
        val view = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(0xCC0A0A0D.toInt())
                cornerRadius = dp(12).toFloat()
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
                    manager.updateViewLayout(view, lp)
                }
            }
            true
        }
        manager.addView(view, lp)
        tv = view
        CrashLogger.log(this, "I", "Overlay", "Overlay ditampilkan")
    }

    private fun update() {
        val v = tv ?: return
        runCatching {
            val s = BoostManager.stats(this)
            val hz = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY).refreshRate.toInt()
            v.text = "TEMP %.1f°C  RAM %d%%\nBATT %d%%%s  %dHz".format(
                s.tempC, s.ramPct, s.batt, if (s.charging) "+" else "", hz
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { tv?.let { wm?.removeView(it) } }
        tv = null
        CrashLogger.log(this, "I", "Overlay", "Overlay dimatikan")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.luki.gamespace.STOP"
        private const val CHANNEL = "gamespace_boost"
        private const val NOTIF_ID = 1001
    }
}
