package com.luki.gamespace.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luki.gamespace.BoostManager
import com.luki.gamespace.GameItem
import com.luki.gamespace.GameRepo
import com.luki.gamespace.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeTab(
    stats: Stats,
    active: Boolean,
    mode: BoostManager.Mode,
    games: List<GameItem>,
    onToggle: () -> Unit,
    onGame: (GameItem) -> Unit,
    onRemove: (GameItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Hero(stats, active, mode, onToggle) }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("GAME LIBRARY", games.size.toString()) }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Belum ada game. Tap + untuk menambahkan.",
                    color = Dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        itemsIndexed(games, key = { _, g -> g.pkg }) { i, g ->
            GameCard(g, i, { onGame(g) }, { onRemove(g) })
        }
        item { AddCard(onAdd) }
    }
}

@Composable
fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Red))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Hero(stats: Stats, active: Boolean, mode: BoostManager.Mode, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoostRing(active, stats.ramPct, onToggle)
        Spacer(Modifier.height(10.dp))
        Text("MODE: ${mode.label.uppercase()}", color = Dim, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("TEMP", "%.1f°C".format(stats.tempC), if (stats.tempC >= 42f) Amber else Color.White, Modifier.weight(1f))
            StatChip("BATT", "${stats.batt}%" + if (stats.charging) " +" else "", Color.White, Modifier.weight(1f))
            StatChip("RAM FREE", "${stats.ramFreeMb}MB", Color.White, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Dim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                    (slideOutVertically(tween(250)) { -it } + fadeOut(tween(150)))
            },
            label = "stat"
        ) { v ->
            Text(v, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BoostRing(active: Boolean, ramPct: Int, onClick: () -> Unit) {
    val density = LocalDensity.current
    val color by animateColorAsState(if (active) Red else Color(0xFF5A5A66), tween(400), label = "col")
    val sweep by animateFloatAsState(ramPct * 3.6f, tween(700), label = "sweep")
    val glow by animateFloatAsState(if (active) 0.40f else 0.10f, tween(500), label = "glow")
    var chase by remember { mutableFloatStateOf(0f) }
    val fg = AppState.foreground

    // Animasi cuma jalan pas boost aktif & app kelihatan, dibatasi ~30fps -> hemat GPU, nggak panas.
    LaunchedEffect(active, fg) {
        if (!active || !fg) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(33)
            val now = System.nanoTime()
            chase = (chase + (now - last) / 2_600_000_000f) % 1f
            last = now
        }
    }

    val dash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 7.dp.toPx())) }
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "sc")

    Box(
        Modifier.size(250.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clickable(interactionSource = src, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.minDimension / 2f
            val stroke = 12.dp.toPx()
            val pad = 28.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val tl = Offset(pad, pad)

            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = glow), Color.Transparent), center = center, radius = half),
                radius = half
            )
            drawCircle(Color(0xFF0E0E12), radius = half - pad - stroke)
            drawArc(
                color = Color(0xFF24242C), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = tl, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )

            val rTick = half - 10.dp.toPx()
            val tickStyle = Stroke(width = 9.dp.toPx(), pathEffect = dash)
            if (active) {
                rotate(chase * 360f, center) {
                    drawCircle(
                        Brush.sweepGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to color, center = center),
                        radius = rTick, center = center, style = tickStyle
                    )
                }
            } else {
                drawCircle(color.copy(alpha = 0.14f), radius = rTick, center = center, style = tickStyle)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (active) "BOOSTED" else "TAP TO BOOST",
                color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
            )
            Text("$ramPct%", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black)
            Text("RAM USED", color = Dim, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(g: GameItem, index: Int, onClick: () -> Unit, onLong: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "in")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .height(112.dp)
            .graphicsLayer {
                alpha = a
                translationY = (1f - a) * 60f
                val k = s * (0.85f + 0.15f * a)
                scaleX = k
                scaleY = k
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Panel2, Panel)))
            .border(1.dp, if (pressed) Red else Color(0x1AFFFFFF), shape)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(g.icon, null, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(8.dp))
        Text(g.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.height(112.dp).clip(shape).border(1.5.dp, Red.copy(alpha = 0.6f), shape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, null, tint = Red, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun AddGameDialog(selected: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<GameItem>?>(null) {
        value = withContext(Dispatchers.Default) { GameRepo.loadInstalled(ctx) }
    }
    var sel by remember { mutableStateOf(selected) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f).clip(RoundedCornerShape(24.dp)).background(Panel).padding(16.dp)
        ) {
            Text("TAMBAH GAME", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.pkg }) { g ->
                            val on = g.pkg in sel
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { sel = if (on) sel - g.pkg else sel + g.pkg }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(g.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (g.isGame) Text("GAME", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                }
                                Checkbox(
                                    checked = on, onCheckedChange = { sel = if (on) sel - g.pkg else sel + g.pkg },
                                    colors = CheckboxDefaults.colors(checkedColor = Red)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("BATAL", color = Dim) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onDone(sel) },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("SIMPAN (${sel.size})", fontWeight = FontWeight.Black) }
            }
        }
    }
}
