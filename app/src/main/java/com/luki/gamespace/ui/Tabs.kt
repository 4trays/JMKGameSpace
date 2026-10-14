package com.luki.gamespace.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luki.gamespace.BoostManager
import com.luki.gamespace.CrashLogger
import com.luki.gamespace.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────────────────── BOOST TAB ─────────────────────────

@Composable
fun BoostTab(
    mode: BoostManager.Mode,
    onMode: (BoostManager.Mode) -> Unit,
    overlayOn: Boolean,
    onOverlay: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms by produceState(Perms()) {
        while (true) {
            value = BoostManager.perms(ctx)
            delay(1000)
        }
    }
    var cleaning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun open(action: String, withPkg: Boolean) {
        runCatching {
            val i = Intent(action)
            if (withPkg) i.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(i)
        }.onFailure { CrashLogger.log(ctx, "E", "Perm", "Gagal buka $action", it) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("PERFORMANCE MODE", "")
        ModeSelector(mode, onMode)
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "desc"
        ) { m -> Text(m.desc, color = Dim, fontSize = 12.sp) }

        SectionTitle("TOOLS", "")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Overlay monitor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Suhu, RAM, baterai, refresh rate di atas game", color = Dim, fontSize = 11.sp)
            }
            Switch(
                checked = overlayOn, onCheckedChange = onOverlay,
                colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White)
            )
        }
        Button(
            onClick = {
                if (!cleaning) {
                    cleaning = true
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { BoostManager.cleanRam(ctx, emptySet()) }
                        result = "${r.first} app dikirim kill  |  +${r.second} MB"
                        cleaning = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (cleaning) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("BERSIHKAN RAM", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        AnimatedVisibility(result != null) {
            Text(result ?: "", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        SectionTitle("IZIN", "")
        PermRow("Tampil di atas app lain", "Wajib untuk overlay monitor", perms.overlay) {
            open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        }
        PermRow("Akses Jangan Ganggu (DND)", "Untuk blokir notifikasi pas main", perms.dnd) {
            open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false)
        }
        PermRow("Ubah setelan sistem", "Untuk brightness max di mode Extreme", perms.write) {
            open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
        }
        PermRow("Notifikasi", "Untuk tombol 'Matikan Boost'", perms.notif) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ModeSelector(mode: BoostManager.Mode, onMode: (BoostManager.Mode) -> Unit) {
    val modes = BoostManager.Mode.values()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Panel2)
    ) {
        val w = maxWidth / modes.size
        val x by animateDpAsState(w * mode.ordinal, spring(dampingRatio = 0.7f), label = "x")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(RedDark, Red)))
        )
        Row(Modifier.fillMaxSize()) {
            modes.forEach { m ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onMode(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.label.uppercase(),
                        color = if (m == mode) Color.White else Dim,
                        fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = Dim, fontSize = 11.sp)
        }
        if (granted) {
            Text("AKTIF", color = Green, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = RoundedCornerShape(12.dp)
            ) { Text("IZINKAN", fontWeight = FontWeight.Black, fontSize = 11.sp) }
        }
    }
}

// ───────────────────────── LOGS TAB ─────────────────────────

@Composable
fun LogsTab() {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val lines by produceState(emptyList<String>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { CrashLogger.read(ctx) }
            delay(1500)
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallBtn("COPY") { clip.setText(AnnotatedString(lines.joinToString("\n"))) }
            SmallBtn("SHARE") { shareText(ctx, lines.joinToString("\n")) }
            SmallBtn("CLEAR") { CrashLogger.clear(ctx) }
            SmallBtn("TEST CRASH", danger = true) { throw RuntimeException("Test crash dari tombol Logs") }
        }
        Spacer(Modifier.height(10.dp))
        if (lines.isEmpty()) {
            Text("Log kosong.", color = Dim, fontSize = 12.sp)
        }
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D0D10)).padding(10.dp)
        ) {
            items(lines.size) { i -> LogLine(lines[i]) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LogLine(line: String) {
    val level = if (line.length > 25 && line[25] == '/') line[24] else ' '
    val c = when (level) {
        'E', 'F' -> Color(0xFFFF5A6E)
        'W' -> Amber
        'I' -> Color(0xFFC8C8D0)
        else -> Color(0xFF8A8A95)
    }
    Text(
        line, color = c, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
        fontWeight = if (level == 'F') FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun SmallBtn(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.clip(shape)
            .background(if (danger) Color(0x33FF1F3D) else Panel2)
            .border(1.dp, if (danger) Red else Color(0x22FFFFFF), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, color = if (danger) Red else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

private fun shareText(ctx: android.content.Context, text: String) {
    runCatching {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.takeLast(60_000))
        }
        ctx.startActivity(Intent.createChooser(i, "Share log"))
    }
}

// ───────────────────────── CRASH SCREEN ─────────────────────────

@Composable
fun CrashScreen(trace: String, onRestart: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(16.dp)
    ) {
        Text("APP CRASH", color = Red, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Maaf, Game Space berhenti. Log sudah tersimpan di tab LOGS.", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D0D10)).padding(10.dp)
        ) {
            Text(
                trace, color = Color(0xFFFF8A98), fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn("COPY") { clip.setText(AnnotatedString(trace)) }
            SmallBtn("SHARE") { shareText(ctx, trace) }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("BUKA ULANG APP", fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
    }
}
