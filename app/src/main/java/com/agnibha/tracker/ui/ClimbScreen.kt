package com.agnibha.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Badges
import com.agnibha.tracker.Scoring
import com.agnibha.tracker.Store
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val SPACING = 92.dp
private val TOP_PAD = 90.dp
private val BOTTOM_PAD = 110.dp

@Composable
fun ClimbScreen() {
    val s = Store.state
    val streak = Store.streak(s)
    val best = maxOf(s.bestStreak, streak)
    val nextCamp = (streak / 7 + 1) * 7
    var view by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Label("The climb")
            Spacer(Modifier.height(6.dp))
            Text(if (streak == 1) "1 day up" else "$streak days up", style = Type.h1)
            Text(
                if (streak == 0) "Lock in tonight's plan to take the first step."
                else "Camp ${nextCamp / 7} in ${nextCamp - streak} day${if (nextCamp - streak == 1) "" else "s"}.",
                style = Type.small,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("$best", "best")
                Metric("${Store.totalCheckIns(s)}", "check-ins")
                Metric("${s.totalPoints}", "points", color = Ink.amber)
                Metric("${Scoring.level(s.totalPoints)}", "level")
            }
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("Trail", "Badges"), view) { view = it }
        }
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { w -> dir * w / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(200)) { w -> -dir * w / 4 } + fadeOut(tween(150)))
            },
            modifier = Modifier.weight(1f),
            label = "climb",
        ) { v ->
            if (v == 0) TrailMap(streak, best, s.username) else BadgeGrid()
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Ink.surface, PxShapeSmall).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Ink.raised else Ink.surface, PxShapeSmall)
                    .clickable { onPick(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = Type.num.copy(fontSize = 15.sp, color = if (on) Ink.amber else Ink.dim))
            }
        }
    }
}

@Composable
private fun TrailMap(streak: Int, best: Int, username: String) {
    val nodes = ((maxOf(streak, best) + 10) / 7 + 1) * 7
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val measurer = rememberTextMeasurer(cacheSize = 160)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(320); frame++ } }
    val twinkle by rememberInfiniteTransition(label = "tw").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "tw",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val viewPx = with(density) { maxHeight.toPx() }
        val spacingPx = with(density) { SPACING.toPx() }
        val topPx = with(density) { TOP_PAD.toPx() }
        val heightDp = TOP_PAD + SPACING * nodes + BOTTOM_PAD
        val amp = widthPx * 0.25f

        fun pos(i: Float): Offset = Offset(widthPx / 2f + amp * sin(i * 0.85f), topPx + (nodes - i) * spacingPx)

        val stars = remember(nodes, widthPx) {
            val r = Random(7)
            List(nodes * 3) { Triple(r.nextFloat(), r.nextFloat(), r.nextInt(3)) }
        }

        LaunchedEffect(streak, viewPx) {
            val y = pos(streak.toFloat()).y - viewPx * 0.6f
            scroll.animateScrollTo(y.roundToInt().coerceIn(0, scroll.maxValue.coerceAtLeast(0)))
        }

        Box(Modifier.fillMaxWidth().verticalScroll(scroll).height(heightDp)) {
            Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                val px = 3.dp.toPx() // the map's pixel size
                fun snap(v: Float) = floor(v / px) * px

                // Stars
                stars.forEachIndexed { i, (sx, sy, k) ->
                    val a = if (i % 5 == 0) 0.25f + 0.75f * twinkle else 0.5f
                    val c = if (k == 0) Ink.amber else Ink.dim
                    drawRect(c, Offset(snap(sx * size.width), snap(sy * size.height)), Size(px, px), alpha = a * 0.6f)
                }

                // Trail: blocks sampled along the curve
                val stepsPerNode = 9
                for (i in 0 until nodes * stepsPerNode) {
                    val f = i / stepsPerNode.toFloat()
                    if (i % stepsPerNode == 0) continue
                    val p = pos(f)
                    val done = f <= streak
                    drawRect(
                        if (done) Ink.amber else Ink.line,
                        Offset(snap(p.x - px), snap(p.y - px)),
                        Size(px * 2, px * 2),
                    )
                }

                // Pines along the outside of each bend
                for (i in 1..nodes step 2) {
                    val p = pos(i + 0.5f)
                    val side = if (sin((i + 0.5f) * 0.85f) > 0) -1 else 1
                    val x = p.x + side * (60.dp.toPx() + (i % 3) * 14.dp.toPx())
                    if (x > 8.dp.toPx() && x < size.width - 30.dp.toPx()) {
                        drawSprite(Sprites.pine, Offset(snap(x), snap(p.y - 20.dp.toPx())), px, muted = i > streak + 3)
                    }
                }

                // Steps and camps
                for (i in 1..nodes) {
                    val c = pos(i.toFloat())
                    if (i == streak) continue
                    if (i % 7 == 0) {
                        val sp = if (i <= streak) Sprites.tent else Sprites.tentTodo
                        drawSprite(sp, Offset(snap(c.x - sp.w * px / 2), snap(c.y - sp.h * px + 4 * px)), px)
                        val label = measurer.measure("Camp ${i / 7}", TextStyle(fontFamily = Pix, fontSize = 15.sp, color = if (i <= streak) Ink.amber else Ink.dim))
                        val right = c.x < size.width / 2
                        val lx = if (right) c.x + 30.dp.toPx() else c.x - 30.dp.toPx() - label.size.width
                        drawText(label, topLeft = Offset(lx, c.y - 22.dp.toPx()))
                        val sub = measurer.measure("day $i", TextStyle(fontFamily = Sans, fontSize = 12.sp, color = Ink.faint))
                        drawText(sub, topLeft = Offset(if (right) lx else c.x - 30.dp.toPx() - sub.size.width, c.y - 2.dp.toPx()))
                    } else {
                        val sp = if (i <= streak) Sprites.stoneDone else Sprites.stoneTodo
                        drawSprite(sp, Offset(snap(c.x - 4 * px), snap(c.y - 4 * px)), px)
                        val n = measurer.measure("$i", TextStyle(fontFamily = Pix, fontSize = 11.sp, color = if (i <= streak) Ink.dim else Ink.faint))
                        val right = c.x < size.width / 2
                        drawText(n, topLeft = Offset(if (right) c.x + 18.dp.toPx() else c.x - 18.dp.toPx() - n.size.width, c.y - n.size.height / 2f))
                    }
                    if (i == best && best > streak) {
                        val b = measurer.measure("your best", TextStyle(fontFamily = Pix, fontSize = 12.sp, color = Ink.dim))
                        val right = c.x < size.width / 2
                        drawText(b, topLeft = Offset(if (right) c.x + 18.dp.toPx() else c.x - 18.dp.toPx() - b.size.width, c.y + 8.dp.toPx()))
                    }
                }

                // Base camp
                val b = pos(0f)
                drawSprite(Sprites.base, Offset(snap(b.x - 7 * px), snap(b.y - 6 * px)), px)

                // Climber
                val a = pos(streak.toFloat())
                val cl = if (frame % 2 == 0) Sprites.climberA else Sprites.climberB
                val cpx = 4.dp.toPx()
                drawSprite(cl, Offset(snap(a.x - cl.w * cpx / 2), snap(a.y - cl.h * cpx + 4 * px)), cpx, flipX = pos(streak + 1f).x < a.x)
                val tag = measurer.measure(
                    if (streak == 0) username else "$username · day $streak",
                    TextStyle(fontFamily = Pix, fontSize = 13.sp, color = Ink.text, textAlign = TextAlign.Center),
                )
                val tx = (a.x - tag.size.width / 2f).coerceIn(4.dp.toPx(), size.width - tag.size.width - 4.dp.toPx())
                drawRect(Ink.surface, Offset(tx - 6.dp.toPx(), a.y + 8.dp.toPx()), Size(tag.size.width + 12.dp.toPx(), tag.size.height + 6.dp.toPx()))
                drawText(tag, topLeft = Offset(tx, a.y + 11.dp.toPx()))
            }
        }
    }
}

@Composable
private fun BadgeGrid() {
    val all = Badges.all(Store.state)
    val earned = all.count { it.earned }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$earned of ${all.size} earned", style = Type.small)
        all.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, b ->
                    val i = r * 3 + c
                    // Stickers: each one sits at its own slight angle, earned ones a little raised.
                    val tilt = listOf(-2.5f, 1.5f, -1f, 2.5f, -1.5f, 1f)[i % 6]
                    PixelBox(
                        Modifier.weight(1f).appear(i).graphicsLayer { rotationZ = if (b.earned) tilt else 0f },
                        color = if (b.earned) Ink.raised else Ink.surface,
                        depth = if (b.earned) 4.dp else 2.dp,
                        padding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp, horizontal = 8.dp),
                        onClick = { Toaster.show(if (b.earned) "${b.name}: ${b.how}." else "${b.how}. Now: ${b.detail}.") },
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (b.earned) Peek(badgeSprite(b.id), size = 52.dp)
                            else PixelSprite(badgeSprite(b.id), Modifier.size(52.dp), muted = true, alpha = 0.7f)
                            Spacer(Modifier.height(8.dp))
                            Text(b.name, style = Type.small.copy(color = if (b.earned) Ink.text else Ink.dim), textAlign = TextAlign.Center, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            if (b.earned) Text("earned", style = Type.label.copy(color = Ink.amber))
                            else PixelMeter(b.progress, Modifier.padding(horizontal = 6.dp), Ink.amber, blocks = 8, height = 6.dp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
