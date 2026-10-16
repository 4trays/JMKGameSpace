package com.jmkgame.space.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jmkgame.space.ImageStore
import com.jmkgame.space.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** State tema: warna aksen, background custom, dan gambar icon. Dibaca langsung oleh UI. */
object CosmeticState {
    var accent by mutableStateOf(Color(0xFFFF1F3DL))
    var bg by mutableStateOf<ImageBitmap?>(null)
    var iconSrc by mutableStateOf<ImageBitmap?>(null)
    var dim by mutableFloatStateOf(0.6f)

    fun loadPrefs(c: Context) {
        accent = Color(Prefs.accent(c))
        dim = Prefs.bgDim(c)
    }

    fun setAccent(c: Context, argb: Long) {
        Prefs.setAccent(c, argb.toInt())
        accent = Color(argb)
    }

    suspend fun loadImages(c: Context) {
        val b = withContext(Dispatchers.IO) { ImageStore.load(c, "bg.jpg") }
        val i = withContext(Dispatchers.IO) { ImageStore.load(c, "icon_src.jpg") }
        bg = b?.asImageBitmap()
        iconSrc = i?.asImageBitmap()
    }
}
