package com.jmkgame.space.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmkgame.space.ImageStore
import com.jmkgame.space.Palette
import com.jmkgame.space.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StyleTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ImageStore.saveFromUri(ctx, uri, "bg.jpg", 1280) }
                if (ok) CosmeticState.loadImages(ctx)
                else Toast.makeText(ctx, "Gagal memuat gambar, cek Logs", Toast.LENGTH_LONG).show()
            }
        }
    }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ImageStore.saveFromUri(ctx, uri, "icon_src.jpg", 512) }
                if (ok) CosmeticState.loadImages(ctx)
                else Toast.makeText(ctx, "Gagal memuat gambar, cek Logs", Toast.LENGTH_LONG).show()
            }
        }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("TEMA WARNA", "")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Palette.list.forEach { (_, argb) ->
                AccentDot(argb) { CosmeticState.setAccent(ctx, argb) }
            }
        }

        SectionTitle("BACKGROUND", "")
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Panel),
            contentAlignment = Alignment.Center
        ) {
            val b = CosmeticState.bg
            if (b != null) {
                Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = CosmeticState.dim)) })
            } else {
                Text("Belum ada background", color = Dim, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { pickBg.launch(imageOnly) },
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("PILIH DARI GALERI", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp) }
            if (CosmeticState.bg != null) {
                SmallBtn("HAPUS", danger = true) {
                    ImageStore.delete(ctx, "bg.jpg")
                    CosmeticState.bg = null
                }
            }
        }
        if (CosmeticState.bg != null) {
            Text("GELAPKAN BACKGROUND", color = Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Slider(
                value = CosmeticState.dim,
                onValueChange = { CosmeticState.dim = it },
                onValueChangeFinished = { Prefs.setBgDim(ctx, CosmeticState.dim) },
                valueRange = 0.15f..0.9f,
                colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Panel2)
            )
        }

        SectionTitle("ICON APP", "")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Panel2),
                contentAlignment = Alignment.Center
            ) {
                val i = CosmeticState.iconSrc
                if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.AddPhotoAlternate, null, tint = Dim, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { pickIcon.launch(imageOnly) },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel2),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("PILIH GAMBAR", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp) }
                Button(
                    onClick = { Toast.makeText(ctx, ImageStore.pinShortcut(ctx), Toast.LENGTH_LONG).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("BUAT SHORTCUT DI HOME", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp) }
            }
        }
        Text(
            "Android nggak ngizinin app ganti icon bawaannya sendiri. Jadi dibuat shortcut baru di home screen " +
                "dengan icon pilihanmu. Icon bawaan bisa kamu sembunyiin lewat setelan launcher.",
            color = Dim, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccentDot(argb: Long, onClick: () -> Unit) {
    val color = Color(argb)
    val selected = CosmeticState.accent == color
    val sc by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = 0.5f), label = "dot")
    Box(
        Modifier.size(44.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable { onClick() }
    )
}
