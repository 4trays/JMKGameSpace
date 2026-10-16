package com.jmkgame.space

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

enum class FpsState { SHIZUKU_READY, SHIZUKU_NO_PERMISSION, SHIZUKU_NOT_RUNNING, ROOT, UNAVAILABLE }

/**
 * FPS game dibaca dari SurfaceFlinger (`dumpsys SurfaceFlinger --latency`).
 * Itu butuh akses shell, jadi cuma bisa lewat Shizuku atau root. App biasa nggak bisa.
 */
object FpsMonitor {
    @Volatile
    var fps = -1

    @Volatile private var rootOk: Boolean? = null
    @Volatile private var rootProbing = false
    private var layerPkg: String? = null
    private var layer: String? = null
    private var misses = 0
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    private fun alive() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun granted() = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizuku() {
        runCatching { Shizuku.requestPermission(1001) }
    }

    fun shizukuInstalled(c: Context) = runCatching {
        @Suppress("DEPRECATION")
        c.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    }.getOrDefault(false)

    fun state(c: Context, probeRoot: Boolean): FpsState {
        if (alive()) return if (granted()) FpsState.SHIZUKU_READY else FpsState.SHIZUKU_NO_PERMISSION
        if (probeRoot && rootOk == null && !rootProbing) {
            rootProbing = true
            Thread {
                rootOk = checkRoot()
                rootProbing = false
            }.start()
        }
        if (rootOk == true) return FpsState.ROOT
        return if (shizukuInstalled(c)) FpsState.SHIZUKU_NOT_RUNNING else FpsState.UNAVAILABLE
    }

    private fun checkRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (t: Throwable) {
        false
    }

    private fun shizukuProcess(cmd: String): Process {
        return Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
    }

    private fun exec(cmd: String, viaRoot: Boolean): String? = try {
        val p = if (viaRoot) Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) else shizukuProcess(cmd)
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out
    } catch (t: Throwable) {
        null
    }

    private fun discover(pkg: String, viaRoot: Boolean): String? {
        val out = exec("dumpsys SurfaceFlinger --list", viaRoot) ?: return null
        val cands = out.lineSequence().map { it.trim() }.filter { it.contains(pkg) }.toList()
        return cands.firstOrNull { it.startsWith("SurfaceView") && it.contains("BLAST") }
            ?: cands.firstOrNull { it.startsWith("SurfaceView") }
            ?: cands.lastOrNull()
    }

    /** Blocking (~50ms). Panggil dari thread worker, sekitar 1x per detik, dan cuma kalau FPS lagi ditampilkan. */
    fun sample(pkg: String, viaRoot: Boolean) {
        try {
            if (layerPkg != pkg || layer == null) {
                layer = discover(pkg, viaRoot)
                layerPkg = pkg
                misses = 0
            }
            val l = layer
            if (l == null) {
                fps = -1
                return
            }
            val out = exec("dumpsys SurfaceFlinger --latency '${l.replace("'", "")}'", viaRoot)
            val ts = ArrayList<Long>()
            out?.lineSequence()?.drop(1)?.forEach { ln ->
                val parts = ln.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val t = parts[1].toLongOrNull()
                    if (t != null && t > 0L && t != Long.MAX_VALUE) ts.add(t)
                }
            }
            ts.sort()
            val last = ts.lastOrNull()
            if (last == null || ts.size < 2) {
                misses++
                if (misses >= 3) layer = null
                fps = -1
                return
            }
            val window = ts.filter { last - it <= 1_000_000_000L }
            val span = (window.last() - window.first()) / 1e9
            fps = if (window.size >= 2 && span > 0.2) ((window.size - 1) / span).roundToInt() else -1
            if (fps >= 0) misses = 0
        } catch (t: Throwable) {
            fps = -1
        }
    }
}
