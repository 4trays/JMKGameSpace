package com.luki.gamespace.ui

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
import com.luki.gamespace.BoostManager
import com.luki.gamespace.CrashLogger
import com.luki.gamespace.GameItem
import com.luki.gamespace.GameRepo
import com.luki.gamespace.Prefs
import com.luki.gamespace.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Prefs.mode(ctx)) }
    var overlayOn by remember { mutableStateOf(Prefs.overlay(ctx)) }
    var games by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var launching by remember { mutableStateOf<GameItem?>(null) }
    var boostActive by remember { mutableStateOf(BoostManager.isActive(ctx)) }
    var crashPending by remember { mutableStateOf(CrashLogger.hasPendingCrash(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<GameItem?>(null) }
    val launchP = remember { Animatable(0f) }

    val stats by produceState(Stats()) {
        while (true) {
            value = withContext(Dispatchers.Default) { BoostManager.stats(ctx) }
            delay(1500)
        }
    }

    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.Default) { GameRepo.loadSaved(ctx) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            boostActive = BoostManager.isActive(ctx)
            delay(1000)
        }
    }

    LaunchedEffect(launching) {
        val g = launching ?: return@LaunchedEffect
        launchP.snapTo(0f)
        val job = async(Dispatchers.Default) {
            runCatching { BoostManager.activate(ctx, mode, overlayOn, setOf(g.pkg)) }
        }
        launchP.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        job.await()
        val ok = BoostManager.launchGame(ctx, g.pkg)
        if (!ok) Toast.makeText(ctx, "Gagal membuka ${g.label}, cek Logs", Toast.LENGTH_LONG).show()
        boostActive = BoostManager.isActive(ctx)
        delay(400)
        launching = null
        launchP.snapTo(0f)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        BackgroundGlow(boostActive)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(boostActive)
            AnimatedVisibility(crashPending) {
                CrashBanner {
                    tab = 2
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
                                        else BoostManager.activate(ctx, mode, overlayOn, emptySet())
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
                            overlayOn = overlayOn,
                            onOverlay = { overlayOn = it; Prefs.setOverlay(ctx, it) }
                        )
                        else -> LogsTab()
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }

        launching?.let { LaunchOverlay(it, launchP.value) }

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
    val inf = rememberInfiniteTransition(label = "bg")
    val pulse by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val a by animateFloatAsState(if (active) 0.38f else 0.16f, tween(800), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Red.copy(alpha = a * pulse), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 1.1f
            )
        )
    }
}

@Composable
private fun Header(active: Boolean) {
    val inf = rememberInfiniteTransition(label = "hdr")
    val shift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "shift")
    val chipBg by animateColorAsState(if (active) Red else Panel2, tween(400), label = "chip")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text("GAME ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp)
                Text("SPACE", color = Red, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp)
            }
            Canvas(Modifier.padding(top = 6.dp).width(130.dp).height(3.dp).clipToBounds()) {
                drawRect(Color(0x33FFFFFF))
                val w = size.width * 0.45f
                val x = (size.width + w) * shift - w
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Red, Color.Transparent), startX = x, endX = x + w),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height)
                )
            }
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

@Composable
private fun LaunchOverlay(g: GameItem, p: Float) {
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = (hypot(size.width, size.height) * p).coerceAtLeast(1f)
            drawCircle(
                Brush.radialGradient(listOf(Red, RedDark, Bg), center = center, radius = r),
                radius = r,
                center = center
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = (p * 2f).coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
            }
        ) {
            Image(g.icon, null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(16.dp))
            Text(g.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("MENGAKTIFKAN BOOST...", color = Color(0xCCFFFFFF), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
}
