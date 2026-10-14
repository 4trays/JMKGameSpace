package com.luki.gamespace

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashLogger {
    private const val FILE = "gamespace.log"
    private const val MAX = 300_000L
    private val lock = Any()

    fun file(c: Context) = File(c.filesDir, FILE)

    fun isCrashProcess(): Boolean = runCatching {
        File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":crash")
    }.getOrDefault(false)

    /** Level: I, W, E, F */
    fun log(c: Context, level: String, tag: String, msg: String, t: Throwable? = null) {
        runCatching {
            synchronized(lock) {
                val f = file(c)
                if (f.exists() && f.length() > MAX) {
                    f.writeText(f.readText().takeLast((MAX / 2).toInt()))
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val sb = StringBuilder()
                sb.append(ts).append(' ').append(level).append('/').append(tag).append(": ").append(msg).append('\n')
                if (t != null) sb.append(t.stackTraceToString()).append('\n')
                f.appendText(sb.toString())
            }
        }
        val prio = when (level) {
            "E", "F" -> Log.ERROR
            "W" -> Log.WARN
            else -> Log.INFO
        }
        Log.println(prio, tag, msg + (if (t != null) "\n" + t.stackTraceToString() else ""))
    }

    fun read(c: Context): List<String> = runCatching {
        val f = file(c)
        if (!f.exists()) emptyList<String>() else f.readLines().takeLast(500)
    }.getOrDefault(emptyList<String>())

    fun clear(c: Context) {
        runCatching { file(c).writeText("") }
    }

    fun hasPendingCrash(c: Context) = c.getSharedPreferences("crash", Context.MODE_PRIVATE).getBoolean("pending", false)

    fun clearPending(c: Context) {
        c.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("pending", false).apply()
    }

    fun install(app: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                log(
                    app, "F", "FATAL",
                    "Uncaught exception in thread '${thread.name}' | ${Build.MANUFACTURER} ${Build.MODEL} | API ${Build.VERSION.SDK_INT}",
                    e
                )
                app.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("pending", true).commit()
                if (!isCrashProcess()) {
                    val trace = "Thread: ${thread.name}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" +
                        e.stackTraceToString()
                    val i = Intent(app, CrashActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("trace", trace.take(30_000))
                    }
                    app.startActivity(i)
                    Thread.sleep(250)
                }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}
