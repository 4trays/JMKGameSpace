package com.luki.gamespace

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class GameItem(val pkg: String, val label: String, val icon: ImageBitmap, val isGame: Boolean = false)

object GameRepo {
    private fun p(c: Context) = c.getSharedPreferences("games", Context.MODE_PRIVATE)

    fun savedPkgs(c: Context): Set<String> = p(c).getStringSet("pkgs", emptySet()) ?: emptySet()

    fun save(c: Context, s: Set<String>) {
        p(c).edit().putStringSet("pkgs", HashSet(s)).apply()
    }

    private fun item(c: Context, pkg: String): GameItem? = runCatching {
        val pm = c.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        @Suppress("DEPRECATION")
        val isGame = ai.category == ApplicationInfo.CATEGORY_GAME
        GameItem(
            pkg,
            pm.getApplicationLabel(ai).toString(),
            pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap(),
            isGame
        )
    }.onFailure { CrashLogger.log(c, "W", "GameRepo", "Gagal load $pkg", it) }.getOrNull()

    fun loadSaved(c: Context): List<GameItem> =
        savedPkgs(c).mapNotNull { item(c, it) }.sortedBy { it.label.lowercase() }

    fun loadInstalled(c: Context): List<GameItem> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val pkgs = c.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != c.packageName }
        return pkgs.mapNotNull { item(c, it) }
            .sortedWith(compareByDescending<GameItem> { it.isGame }.thenBy { it.label.lowercase() })
    }
}
