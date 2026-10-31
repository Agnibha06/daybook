package com.agnibha.tracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.agnibha.tracker.Scoring
import com.agnibha.tracker.Store
import kotlin.math.roundToInt
import kotlin.math.sin

private val SPACING = 86.dp
private val TOP_PAD = 70.dp
private val BOTTOM_PAD = 90.dp

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun ClimbScreen() {
    val s = Store.state
    val streak = Store.streak(s)
    val best = maxOf(s.bestStreak, streak)
    val nextCamp = (streak / 7 + 1) * 7

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Label("The climb")
            Spacer(Modifier.height(4.dp))
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
        }
        ClimbMap(streak, best, s.username, Modifier.weight(1f))
    }
}

@Composable
private fun ClimbMap(streak: Int, best: Int, username: String, modifier: Modifier) {
    val nodes = ((maxOf(streak, best) + 10) / 7 + 1) * 7
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val halo by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "halo",
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val viewPx = with(density) { maxHeight.toPx() }
        val spacingPx = with(density) { SPACING.toPx() }
        val topPx = with(density) { TOP_PAD.toPx() }
        val heightDp = TOP_PAD + SPACING * nodes + BOTTOM_PAD
        val amp = widthPx * 0.26f

        fun pos(i: Int): Offset = Offset(
            widthPx / 2f + amp * sin(i * 0.85f),
            topPx + (nodes - i) * spacingPx,
        )

        LaunchedEffect(streak, viewPx) {
            val y = pos(streak).y - viewPx * 0.55f
            scroll.scrollTo(y.roundToInt().coerceIn(0, scroll.maxValue.coerceAtLeast(0)))
        }

        Box(Modifier.fillMaxWidth().verticalScroll(scroll).height(heightDp)) {
            Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                // Contour lines, like a survey map of the climb.
                val lineCol = Ink.line.copy(alpha = 0.55f)
                var y = topPx * 0.6f
                var k = 0
                while (y < size.height) {
                    val p = Path()
                    val steps = 24
                    for (j in 0..steps) {
                        val x = size.width * j / steps
                        val yy = y + 14.dp.toPx() * sin(j * 0.55f + k * 1.3f) + 6.dp.toPx() * sin(j * 1.7f + k)
                        if (j == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
                    }
                    drawPath(p, lineCol, style = Stroke(1.dp.toPx()))
                    y += spacingPx * 1.15f
                    k++
                }

                // Trail
                fun trail(from: Int, to: Int): Path {
                    val p = Path()
                    val a = pos(from)
                    p.moveTo(a.x, a.y)
                    for (i in from + 1..to) {
                        val prev = pos(i - 1)
                        val cur = pos(i)
                        val midY = (prev.y + cur.y) / 2f
                        p.cubicTo(prev.x, midY, cur.x, midY, cur.x, cur.y)
                    }
                    return p
                }
                drawPath(
                    trail(maxOf(streak, 0), nodes), Ink.faint.copy(alpha = 0.6f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx()))),
                )
                if (streak > 0) drawPath(trail(0, streak), Ink.amber, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))

                // Steps
                for (i in 1..nodes) {
                    val c = pos(i)
                    val camp = i % 7 == 0
                    val r = (if (camp) 19.dp else 13.dp).toPx()
                    if (i <= streak) {
                        drawCircle(Ink.amber, r, c)
                    } else {
                        drawCircle(Ink.surface, r, c)
                        drawCircle(if (camp) Ink.dim else Ink.line, r, c, style = Stroke(2.dp.toPx()))
                    }
                }
                // Base camp
                val b = pos(0)
                drawCircle(Ink.raised, 22.dp.toPx(), b)
                drawCircle(if (streak > 0) Ink.amber else Ink.line, 22.dp.toPx(), b, style = Stroke(2.dp.toPx()))
            }

            // Numbers and labels on top of the canvas.
            for (i in 1..nodes) {
                val c = pos(i)
                val camp = i % 7 == 0
                val d = if (camp) 38.dp else 26.dp
                val half = with(density) { (d / 2).toPx() }
                // The avatar sits on the current step, so skip its number.
                if (i != streak) Box(
                    Modifier.offset { IntOffset((c.x - half).roundToInt(), (c.y - half).roundToInt()) }.size(d),
                    contentAlignment = Alignment.Center,
                ) {
                    if (camp && i > streak) PeakGlyph(16.dp, Ink.dim)
                    else Text(
                        "$i",
                        style = Type.num.copy(
                            fontSize = Type.label.fontSize,
                            color = if (i <= streak) Ink.bg else Ink.faint,
                        ),
                    )
                }
                if (camp) {
                    val leftSide = c.x > widthPx / 2f
                    val labelW = 96.dp
                    val lx = if (leftSide) c.x - with(density) { (labelW + 30.dp).toPx() } else c.x + with(density) { 30.dp.toPx() }
                    Column(
                        Modifier.offset { IntOffset(lx.roundToInt(), (c.y - with(density) { 18.dp.toPx() }).roundToInt()) }.width(labelW),
                        horizontalAlignment = if (leftSide) Alignment.End else Alignment.Start,
                    ) {
                        Text("Camp ${i / 7}", style = Type.title.copy(color = if (i <= streak) Ink.amber else Ink.text, fontWeight = FontWeight.SemiBold))
                        Text("Week ${i / 7} · day $i", style = Type.small)
                    }
                }
                if (i == best && best > streak) {
                    val bx = c.x + with(density) { (if (c.x > widthPx / 2f) -110.dp else 24.dp).toPx() }
                    Text(
                        "Your best",
                        style = Type.small.copy(color = Ink.dim),
                        modifier = Modifier.offset { IntOffset(bx.roundToInt(), (c.y - with(density) { 9.dp.toPx() }).roundToInt()) },
                    )
                }
            }

            // Base label
            val b = pos(0)
            Text(
                "Base",
                style = Type.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset { IntOffset((b.x - with(density) { 40.dp.toPx() }).roundToInt(), (b.y + with(density) { 28.dp.toPx() }).roundToInt()) }
                    .width(80.dp),
            )

            // Avatar
            val a = pos(streak)
            val av = 54.dp
            val avHalf = with(density) { (av / 2).toPx() }
            Box(
                Modifier.offset { IntOffset((a.x - avHalf).roundToInt(), (a.y - avHalf).roundToInt()) }.size(av),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(av).scale(halo).clip(CircleShape).background(Ink.amber.copy(alpha = 0.16f)))
                Box(
                    Modifier.size(av - 6.dp).clip(CircleShape).background(Ink.raised).border(3.dp, Ink.amber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials(username), style = Type.title.copy(fontWeight = FontWeight.SemiBold, color = Ink.text))
                }
            }
            Text(
                if (streak == 0) username else "$username · day $streak",
                style = Type.small.copy(color = Ink.text, fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset { IntOffset((a.x - with(density) { 80.dp.toPx() }).roundToInt(), (a.y + avHalf + with(density) { 6.dp.toPx() }).roundToInt()) }
                    .width(160.dp),
            )
        }
    }
}
