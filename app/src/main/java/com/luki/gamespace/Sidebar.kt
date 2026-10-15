package com.luki.gamespace

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Smart Sidebar: pill kecil di tepi layar + panel kontrol yang slide keluar.
 * Pakai View biasa (bukan Compose) biar ringan dan nggak bikin panas.
 */
class Sidebar(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onStop: () -> Unit,
    private val onChanged: () -> Unit
) {
    private val red = 0xFFFF1F3D.toInt()
    private val redDark = 0xFF8E0A1E.toInt()
    private val panelBg = 0xF2111116.toInt()
    private val tileBg = 0xFF1F1F27.toInt()
    private val dim = 0xFF9A9AA6.toInt()
    private val green = 0xFF2EE59D.toInt()
    private val mainH = Handler(Looper.getMainLooper())

    val side: String = Prefs.side(ctx)
    private val left = side == "L"
    var gamePkg: String? = null

    private var open = false
    val isOpen: Boolean get() = open

    private var handle: FrameLayout? = null
    private var handleLp: WindowManager.LayoutParams? = null
    private var root: FrameLayout? = null
    private var card: View? = null
    private var scrim: View? = null

    private var tTemp: TextView? = null
    private var tRam: TextView? = null
    private var tBatt: TextView? = null
    private var tHz: TextView? = null
    private var modeViews: List<TextView> = emptyList()
    private var btnDnd: TextView? = null
    private var btnHud: TextView? = null
    private var btnBoost: TextView? = null

    private var dragging = false
    private var swipeOpened = false
    private var downX = 0f
    private var downY = 0f
    private var startY = 0

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun screenH() = ctx.resources.displayMetrics.heightPixels

    private fun rounded(color: Int, r: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = r.toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun tv(t: String, sp: Float, color: Int, bold: Boolean = false, italic: Boolean = false) =
        TextView(ctx).apply {
            text = t
            textSize = sp
            setTextColor(color)
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(Typeface.DEFAULT, style)
            includeFontPadding = false
        }

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
    ).apply {
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // ───────────────────────── HANDLE ─────────────────────────

    fun showHandle() {
        if (handle != null) return
        val w = dp(28)
        val h = dp(72)
        val lp = overlayParams(
            w, h,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        ).apply {
            gravity = Gravity.TOP or (if (left) Gravity.START else Gravity.END)
            x = 0
            y = (Prefs.handleY(ctx) * screenH()).toInt().coerceIn(0, (screenH() - h).coerceAtLeast(0))
        }
        val fl = FrameLayout(ctx)
        val pill = View(ctx).apply { background = rounded(0xCCFF1F3D.toInt(), dp(3)) }
        fl.addView(
            pill,
            FrameLayout.LayoutParams(dp(5), dp(44), Gravity.CENTER_VERTICAL or (if (left) Gravity.START else Gravity.END))
        )
        attachTouch(fl, lp)
        fl.alpha = 0f
        wm.addView(fl, lp)
        fl.animate().alpha(1f).setDuration(250).start()
        handle = fl
        handleLp = lp
    }

    private fun attachTouch(v: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startY = lp.y
                    dragging = false; swipeOpened = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && !swipeOpened && abs(dy) > slop && abs(dy) > abs(dx)) dragging = true
                    if (dragging) {
                        val maxY = (screenH() - lp.height).coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(v, lp) }
                    } else if (!swipeOpened) {
                        val inward = if (left) dx else -dx
                        if (inward > dp(14)) {
                            swipeOpened = true
                            openPanel()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) Prefs.setHandleY(ctx, lp.y.toFloat() / screenH())
                    else if (!swipeOpened) openPanel()
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> dragging = false
            }
            true
        }
    }

    /** Dipanggil tiap tick: sesuaikan posisi handle kalau layar diputar. */
    fun clampHandle() {
        val h = handle ?: return
        val lp = handleLp ?: return
        if (dragging || open) return
        val maxY = (screenH() - lp.height).coerceAtLeast(0)
        val target = (Prefs.handleY(ctx) * screenH()).toInt().coerceIn(0, maxY)
        if (abs(target - lp.y) > dp(2)) {
            lp.y = target
            runCatching { wm.updateViewLayout(h, lp) }
        }
    }

    // ───────────────────────── PANEL ─────────────────────────

    private fun openPanel() {
        if (open) return
        open = true
        runCatching { buildPanel() }.onFailure {
            open = false
            CrashLogger.log(ctx, "E", "Sidebar", "Gagal buka panel", it)
        }
    }

    private fun section(t: String) = tv(t, 10f, dim, bold = true).apply {
        letterSpacing = 0.15f
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun btn(text: String, onClick: () -> Unit) = tv(text, 11f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(tileBg, dp(12))
        setPadding(dp(4), dp(12), dp(4), dp(12))
        setOnClickListener { onClick() }
    }

    private fun pair(a: View, b: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(4), 0) })
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, 0, 0) })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) }
    }

    private fun seek(max: Int, cur: Int, onChange: (Int) -> Unit): SeekBar = SeekBar(ctx).apply {
        this.max = max
        progress = cur
        progressTintList = ColorStateList.valueOf(red)
        thumbTintList = ColorStateList.valueOf(red)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) runCatching { onChange(p) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun buildPanel() {
        val r = FrameLayout(ctx)
        val sc = View(ctx).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            setOnClickListener { closePanel() }
        }
        r.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(ctx).apply {
            background = rounded(panelBg, dp(22), 0x55FF1F3D)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Header
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("GAME ", 17f, Color.WHITE, bold = true, italic = true))
        head.addView(tv("SPACE", 17f, red, bold = true, italic = true))
        head.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(tv("✕", 18f, dim, bold = true).apply {
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { closePanel() }
        })
        col.addView(head)

        val label = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(gamePkg ?: "", 0)).toString()
        }.getOrDefault(gamePkg ?: "")
        col.addView(tv(label, 11f, dim).apply { setPadding(0, dp(4), 0, dp(10)) })

        // Stats
        val statRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun tile(title: String): TextView {
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = rounded(tileBg, dp(12))
                setPadding(0, dp(8), 0, dp(8))
            }
            box.addView(tv(title, 8f, dim, bold = true))
            val v = tv("--", 13f, Color.WHITE, bold = true).apply { setPadding(0, dp(3), 0, 0) }
            box.addView(v)
            statRow.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            return v
        }
        tTemp = tile("TEMP"); tRam = tile("RAM"); tBatt = tile("BATT"); tHz = tile("Hz")
        col.addView(statRow)

        // Mode
        col.addView(section("PERFORMANCE MODE"))
        val seg = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(tileBg, dp(12))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        modeViews = BoostManager.Mode.values().map { m ->
            tv(m.label.uppercase(), 10f, dim, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                setOnClickListener { chooseMode(m) }
            }.also { seg.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        }
        col.addView(seg)

        // Toggles
        col.addView(section("QUICK TOOLS"))
        val dnd = btn("DND") { toggleDnd() }.also { btnDnd = it }
        val hud = btn("HUD") {
            Prefs.setOverlay(ctx, !Prefs.overlay(ctx))
            refreshToggles()
            onChanged()
        }.also { btnHud = it }
        col.addView(pair(dnd, hud))
        val ram = btn("BERSIHKAN RAM") { cleanRam() }
        val boost = btn("BOOST") { toggleBoost() }.also { btnBoost = it }
        col.addView(pair(ram, boost))

        // Brightness
        col.addView(section("BRIGHTNESS"))
        if (Settings.System.canWrite(ctx)) {
            val cur = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
            col.addView(seek(255, cur) { v ->
                val cr = ctx.contentResolver
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(1))
            })
        } else {
            col.addView(tv("Izin 'Ubah setelan sistem' belum aktif (tab BOOST)", 10f, dim))
        }

        // Volume
        col.addView(section("VOLUME"))
        val audio = ctx.getSystemService(AudioManager::class.java)
        col.addView(seek(
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        ) { v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) })

        // Footer
        val openApp = btn("BUKA GAME SPACE") {
            runCatching {
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            closePanel()
        }
        val stop = btn("MATIKAN") { onStop() }.apply { background = rounded(redDark, dp(12)) }
        col.addView(pair(openApp, stop))

        paintModes()
        refreshToggles()

        val rlp = overlayParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        val cardW = dp(310)
        val clp = FrameLayout.LayoutParams(
            cardW, ViewGroup.LayoutParams.WRAP_CONTENT,
            (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        ).apply { setMargins(dp(10), dp(10), dp(10), dp(10)) }
        r.addView(scroll, clp)

        wm.addView(r, rlp)
        root = r; card = scroll; scrim = sc
        handle?.visibility = View.INVISIBLE

        scroll.translationX = (if (left) -1f else 1f) * (cardW + dp(20))
        scroll.animate().translationX(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        sc.animate().alpha(0.45f).setDuration(240).start()
    }

    fun closePanel() {
        if (!open) return
        open = false
        handle?.visibility = View.VISIBLE
        val c = card
        if (c == null) { removeRoot(); return }
        scrim?.animate()?.alpha(0f)?.setDuration(200)?.start()
        val to = (if (left) -1f else 1f) * dp(340)
        c.animate().translationX(to).setDuration(200).withEndAction { removeRoot() }.start()
    }

    private fun removeRoot() {
        runCatching { root?.let { wm.removeView(it) } }
        root = null; card = null; scrim = null
        modeViews = emptyList()
        tTemp = null; tRam = null; tBatt = null; tHz = null
        btnDnd = null; btnHud = null; btnBoost = null
    }

    fun destroy() {
        open = false
        runCatching { card?.animate()?.cancel() }
        removeRoot()
        runCatching { handle?.let { wm.removeView(it) } }
        handle = null
        handleLp = null
    }

    // ───────────────────────── ACTIONS ─────────────────────────

    private fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    private fun paintModes() {
        val cur = Prefs.mode(ctx)
        BoostManager.Mode.values().forEachIndexed { i, m ->
            val v = modeViews.getOrNull(i) ?: return@forEachIndexed
            val on = m == cur
            v.setTextColor(if (on) Color.WHITE else dim)
            v.background = if (on) rounded(red, dp(10)) else null
        }
    }

    private fun chooseMode(m: BoostManager.Mode) {
        Prefs.setMode(ctx, m)
        paintModes()
        val auto = if (BoostManager.isActive(ctx)) BoostManager.isAuto(ctx) else true
        val pkgs = setOfNotNull(gamePkg)
        thread {
            BoostManager.activate(ctx, m, auto, pkgs)
            mainH.post { refreshToggles(); toast("Mode ${m.label}") }
        }
    }

    private fun toggleDnd() {
        val r = runCatching { BoostManager.toggleDnd(ctx) }.getOrNull()
        if (r == null) toast("Izin DND belum aktif (tab BOOST)")
        refreshToggles()
    }

    private fun cleanRam() {
        val pkgs = setOfNotNull(gamePkg)
        toast("Membersihkan RAM...")
        thread {
            val r = BoostManager.cleanRam(ctx, pkgs)
            mainH.post { toast("${r.first} app ditutup, +${r.second} MB") }
        }
    }

    private fun toggleBoost() {
        val pkgs = setOfNotNull(gamePkg)
        thread {
            if (BoostManager.isActive(ctx)) BoostManager.deactivate(ctx)
            else BoostManager.activate(ctx, Prefs.mode(ctx), true, pkgs)
            mainH.post { refreshToggles() }
        }
    }

    private fun refreshToggles() {
        fun paint(b: TextView?, label: String, on: Boolean) {
            b ?: return
            b.text = "$label: ${if (on) "ON" else "OFF"}"
            b.setTextColor(if (on) green else Color.WHITE)
        }
        paint(btnDnd, "DND", BoostManager.isDndOn(ctx))
        paint(btnHud, "HUD", Prefs.overlay(ctx))
        paint(btnBoost, "BOOST", BoostManager.isActive(ctx))
    }

    fun refresh(s: Stats, hz: Int) {
        if (!open) return
        tTemp?.text = "%.1f°".format(s.tempC)
        tTemp?.setTextColor(if (s.tempC >= 42f) 0xFFFFB020.toInt() else Color.WHITE)
        tRam?.text = "${s.ramPct}%"
        tBatt?.text = "${s.batt}%" + if (s.charging) "+" else ""
        tHz?.text = hz.toString()
        refreshToggles()
    }
}
