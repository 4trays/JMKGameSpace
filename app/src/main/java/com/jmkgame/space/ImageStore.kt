package com.jmkgame.space

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File
import kotlin.math.max

object Palette {
    val list: List<Pair<String, Long>> = listOf(
        "Merah" to 0xFFFF1F3DL,
        "Oranye" to 0xFFFF7A1AL,
        "Kuning" to 0xFFFFC21AL,
        "Hijau" to 0xFF19E68CL,
        "Cyan" to 0xFF1FD1FFL,
        "Biru" to 0xFF3D7BFFL,
        "Ungu" to 0xFFB05CFFL,
        "Pink" to 0xFFFF4FA3L
    )
}

object ImageStore {
    /** Simpan gambar ke folder app di Android/data (diperkecil + dirotasi sesuai EXIF). */
    fun saveFromUri(c: Context, uri: Uri, name: String, maxSide: Int): Boolean {
        return try {
            val cr = c.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

            val orient = runCatching {
                cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
            }.getOrNull() ?: 1
            val deg = when (orient) {
                6 -> 90f
                3 -> 180f
                8 -> 270f
                else -> 0f
            }
            val m = Matrix()
            if (deg != 0f) m.postRotate(deg)
            val longest = max(raw.width, raw.height)
            if (longest > maxSide) {
                val sc = maxSide.toFloat() / longest
                m.postScale(sc, sc)
            }
            val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            LocalDataStore.file(c, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", "Image", "Gagal simpan gambar $name", t)
            false
        }
    }

    fun load(c: Context, name: String): Bitmap? = runCatching {
        val f = LocalDataStore.file(c, name)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    fun delete(c: Context, name: String) {
        runCatching { LocalDataStore.file(c, name).delete() }
    }

    private fun roundedSquare(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val s = max(size.toFloat() / src.width, size.toFloat() / src.height)
        val m = Matrix().apply {
            setScale(s, s)
            postTranslate((size - src.width * s) / 2f, (size - src.height * s) / 2f)
        }
        shader.setLocalMatrix(m)
        paint.shader = shader
        Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, paint)
        return out
    }

    /** Android nggak bolehin app ganti icon-nya sendiri, jadi bikin shortcut baru di home dengan icon custom. */
    fun pinShortcut(c: Context): String {
        return try {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(c)) {
                return "Launcher kamu belum support pin shortcut"
            }
            val src = load(c, "icon_src.jpg") ?: return "Pilih gambar icon dulu"
            val icon = roundedSquare(src, 192)
            val intent = Intent(c, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            val info = ShortcutInfoCompat.Builder(c, "custom_icon")
                .setShortLabel("JMK Game Space")
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(c, info, null)
            "Konfirmasi pin shortcut muncul, tap Tambahkan"
        } catch (t: Throwable) {
            CrashLogger.log(c, "E", "Image", "Pin shortcut gagal", t)
            "Gagal bikin shortcut, cek Logs"
        }
    }
}
