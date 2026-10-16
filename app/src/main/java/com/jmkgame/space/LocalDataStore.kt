package com.jmkgame.space

import android.content.Context
import android.os.Build
import android.app.ActivityManager
import org.json.JSONObject
import java.io.File

object LocalDataStore {
    fun directory(c: Context): File {
        val external = c.getExternalFilesDir(null)
        val directory = if (external != null && (external.canWrite() || external.mkdirs())) external else c.filesDir
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    fun file(c: Context, name: String): File {
        val target = File(directory(c), name)
        val legacy = File(c.filesDir, name)
        if (!target.exists() && legacy.isFile) {
            runCatching { legacy.copyTo(target, overwrite = false) }
        }
        return target
    }

    fun saveDeviceProfile(c: Context) {
        runCatching {
            val memory = c.getSystemService(ActivityManager::class.java)
                .let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo) }
            val config = c.resources.configuration
            val pm = c.packageManager
            val type = when {
                pm.hasSystemFeature("android.software.leanback") -> "tv"
                pm.hasSystemFeature("android.hardware.type.watch") -> "watch"
                pm.hasSystemFeature("android.hardware.type.automotive") -> "automotive"
                config.smallestScreenWidthDp >= 600 -> "tablet"
                else -> "phone"
            }
            val profile = JSONObject()
                .put("package_name", c.packageName)
                .put("device_type", type)
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("android_version", Build.VERSION.RELEASE)
                .put("api_level", Build.VERSION.SDK_INT)
                .put("total_ram_mb", memory.totalMem / (1024 * 1024))
                .put("screen_width_dp", config.screenWidthDp)
                .put("screen_height_dp", config.screenHeightDp)
                .put("smallest_screen_width_dp", config.smallestScreenWidthDp)
            file(c, "device_profile.json").writeText(profile.toString(2))
        }.onFailure { CrashLogger.log(c, "W", "DeviceProfile", "Gagal menyimpan profil perangkat", it) }
    }
}