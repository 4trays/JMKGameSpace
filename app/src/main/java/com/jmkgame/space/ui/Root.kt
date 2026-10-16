package com.jmkgame.space.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmkgame.space.BoostManager
import com.jmkgame.space.CrashLogger
import com.jmkgame.space.GameItem
import com.jmkgame.space.GameRepo
import com.jmkgame.space.Prefs
import com.jmkgame.space.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.pow

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Prefs.mode(ctx)) }
        var games by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var launching by remember { mutableStateOf<GameItem?>(null) }
    var boostActive by remember { mutableStateOf(BoostManager.isActive(ctx)) }
    var crashPending by remember { mutableStateOf(CrashLogger.hasPendingCrash(ctx)) }
    var sidebarOn by remember { mutableStateOf(Prefs.sidebar(ctx)) }
    var hudOn by remember { mutableStateOf(Prefs.overlay(ctx)) }
    var autoOn by remember { mutableStateOf(Prefs.autoBoost(ctx)) }
    var fpsOn by remember { mutableStateOf(Prefs.fps(ctx)) }
    var side by remember { mutableStateOf(Prefs.side(ctx)) }
    val fg = AppState.foreground
    var showAdd by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<GameItem?>(null) }
    val launchP = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    val stats by produceState(Stats(), fg) {
        if (fg) {
            while (true) {
                value = withContext(Dispatchers.Default) { BoostManager.stats(ctx) }
                delay(2000)
            }
        }
    }

    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.Default) { GameRepo.loadSaved(ctx) }
    }
    LaunchedEffect(Unit) { CosmeticState.loadImages(ctx) }
    LaunchedEffect(fg) {
        if (!fg) return@LaunchedEffect
        while (true) {
            boostActive = BoostManager.isActive(ctx)
            sidebarOn = Prefs.sidebar(ctx)
            hudOn = Prefs.overlay(ctx)
            BoostManager.syncService(ctx)
            delay(1000)
        }
    }

    LaunchedEffect(launching) {
        val g = launching ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launchP.snapTo(0f)
        BoostManager.syncService(ctx)
        val svc = BoostManager.serviceWanted(ctx)
        val job = async(Dispatchers.Default) {
            runCatching { BoostManager.activate(ctx, mode, svc, setOf(g.pkg)) }
        }
        launchP.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        job.await()
        val ok = BoostManager.launchGame(ctx, g.pkg)
        boostActive = BoostManager.isActive(ctx)
        if (!ok) {
            Toast.makeText(ctx, "Gagal membuka ${g.label}, cek Logs", Toast.LENGTH_LONG).show()
            launching = null
            launchP.snapTo(0f)
        } else {
            // tutup UI biar memori kosong; sidebar tetap hidup di service
            delay(120)
            ctx.findActivity()?.finish()
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        val bgImg = CosmeticState.bg
        if (bgImg != null) {
            Image(bgImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = CosmeticState.dim)) })
        }
        BackgroundGlow(boostActive)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(boostActive)
            AnimatedVisibility(crashPending) {
                CrashBanner {
                    tab = 3
                    CrashLogger.clearPending(ctx)
                    crashPending = false
                }
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { it / 4 * dir } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(200)))
                    },
                    label = "tab"
                ) { t ->
                    when (t) {
                        0 -> HomeTab(
                            stats = stats,
                            active = boostActive,
                            mode = mode,
                            games = games,
                            onToggle = {
                                scope.launch {
                                    withContext(Dispatchers.Default) {
                                        if (BoostManager.isActive(ctx)) BoostManager.deactivate(ctx)
                                        else BoostManager.activate(ctx, mode, false, emptySet())
                                    }
                                    boostActive = BoostManager.isActive(ctx)
                                }
                            },
                            onGame = { if (launching == null) launching = it },
                            onRemove = { removeTarget = it },
                            onAdd = { showAdd = true }
                        )
                        1 -> BoostTab(
                            mode = mode,
                            onMode = { mode = it; Prefs.setMode(ctx, it) },
                            sidebarOn = sidebarOn,
                            onSidebar = { sidebarOn = it; Prefs.setSidebar(ctx, it) },
                            hudOn = hudOn,
                            onHud = { hudOn = it; Prefs.setOverlay(ctx, it) },
                            autoOn = autoOn,
                            onAuto = { autoOn = it; Prefs.setAutoBoost(ctx, it) },
                            side = side,
                            onSide = { side = it; Prefs.setSide(ctx, it) },
                            fpsOn = fpsOn,
                            onFps = { fpsOn = it; Prefs.setFps(ctx, it) }
                        )
                        2 -> StyleTab()
                        else -> LogsTab()
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }

        launching?.let { LaunchOverlay(it, mode, launchP.asState()) }

        if (showAdd) {
            AddGameDialog(
                selected = games.map { it.pkg }.toSet(),
                onDismiss = { showAdd = false },
                onDone = { set ->
                    showAdd = false
                    GameRepo.save(ctx, set)
                    scope.launch {
                        games = withContext(Dispatchers.Default) { GameRepo.loadSaved(ctx) }
                    }
                }
            )
        }

        removeTarget?.let { g ->
            AlertDialog(
                onDismissRequest = { removeTarget = null },
                containerColor = Panel2,
                title = { Text("Hapus dari library?", color = Color.White) },
                text = { Text(g.label, color = Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        GameRepo.save(ctx, games.map { it.pkg }.toSet() - g.pkg)
                        games = games.filter { it.pkg != g.pkg }
                        removeTarget = null
                    }) { Text("HAPUS", color = Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { removeTarget = null }) { Text("BATAL", color = Dim) }
                }
            )
        }
    }
}

@Composable
private fun BackgroundGlow(active: Boolean) {
    val a by animateFloatAsState(if (active) 0.34f else 0.16f, tween(800), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Red.copy(alpha = a), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 1.1f
            )
        )
    }
}

@Composable
private fun Header(active: Boolean) {
    val chipBg by animateColorAsState(if (active) Red else Panel2, tween(400), label = "chip")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text("JMK ", color = Red, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 1.sp)
                Text("GAME SPACE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 1.sp)
            }
            Box(
                Modifier.padding(top = 6.dp).width(130.dp).height(3.dp)
                    .background(Brush.horizontalGradient(listOf(Red, Color.Transparent)))
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(chipBg).padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                if (active) "BOOST ON" else "BOOST OFF",
                color = if (active) Color.White else Dim,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun CrashBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(RedDark).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.BugReport, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("App sempat crash. Tap untuk lihat log.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    val items = listOf(
        "GAMES" to Icons.Filled.SportsEsports,
        "BOOST" to Icons.Filled.Bolt,
        "STYLE" to Icons.Filled.Palette,
        "LOGS" to Icons.Filled.BugReport
    )
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101014)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == sel
            val c by animateColorAsState(if (on) Red else Dim, tween(300), label = "c")
            val sc by animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.45f), label = "sc")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSel(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = c, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc })
                Text(label, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x.pow(3) + c1 * x.pow(2)
}

/**
 * Animasi masuk game. Semua nilai animasi dibaca di fase draw / graphicsLayer saja
 * (nggak ada recomposition per frame), cuma 1 Canvas + 1 icon, total < 1 detik.
 */
@Composable
private fun LaunchOverlay(g: GameItem, mode: BoostManager.Mode, p: State<Float>) {
    val stage by remember { derivedStateOf { if (p.value < 0.35f) 0 else if (p.value < 0.7f) 1 else 2 } }
    val label = when (stage) {
        0 -> if (mode == BoostManager.Mode.STANDARD) "MENYIAPKAN" else "MEMBERSIHKAN RAM"
        1 -> if (mode == BoostManager.Mode.STANDARD) "MEMUAT SIDEBAR" else "MODE ${mode.label.uppercase()}"
        else -> "SIAP MAIN!"
    }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pv = p.value
            drawRect(Bg.copy(alpha = (pv * 3f).coerceIn(0f, 0.96f)))
            val gr = size.minDimension * (0.35f + 0.6f * pv)
            drawCircle(
                Brush.radialGradient(
                    listOf(Red.copy(alpha = 0.5f * (pv * 2f).coerceIn(0f, 1f)), Color.Transparent),
                    center = center, radius = gr
                ),
                radius = gr, center = center
            )
            val lineW = 240.dp.toPx()
            for (i in 0 until 5) {
                val y = size.height * (0.2f + i * 0.15f)
                val x = ((pv * 1.6f + i * 0.23f) % 1f) * (size.width + lineW) - lineW
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Red.copy(alpha = 0.55f), Color.Transparent),
                        startX = x, endX = x + lineW
                    ),
                    topLeft = Offset(x, y), size = Size(lineW, 2.dp.toPx())
                )
            }
            val ar = 74.dp.toPx()
            val st = 5.dp.toPx()
            val tl = Offset(center.x - ar, center.y - ar)
            val sz = Size(ar * 2, ar * 2)
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, tl, sz, style = Stroke(st))
            drawArc(Red, -90f, 360f * pv, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
        }
        Image(
            g.icon, null,
            Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).graphicsLayer {
                val pv = p.value
                val intro = (pv / 0.4f).coerceIn(0f, 1f)
                val zoom = 1f + 0.35f * ((pv - 0.85f) / 0.15f).coerceIn(0f, 1f)
                val sc = (0.4f + 0.6f * easeOutBack(intro)) * zoom
                scaleX = sc
                scaleY = sc
                alpha = intro
            }
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 125.dp).graphicsLayer { alpha = (p.value * 3f).coerceIn(0f, 1f) }
        ) {
            Text(g.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(label, color = Red, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
