package com.agnibha.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Task
import com.agnibha.tracker.TaskResult

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ink.faint) {
    Text(text.uppercase(), style = Type.label.copy(color = color), modifier = modifier)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Ink.amber else Ink.raised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.title.copy(color = if (enabled) Ink.bg else Ink.faint, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = Ink.text, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.title.copy(color = color))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = Ink.amber, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.18f) else Ink.raised)
            .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = Type.small.copy(color = if (selected) color else Ink.dim, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Ring(
    progress: Float,
    size: Dp,
    stroke: Dp,
    color: Color,
    track: Color = Ink.line,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink.text) {
    Column(modifier) {
        Text(value, style = Type.num.copy(fontSize = 20.sp, color = color))
        Spacer(Modifier.height(2.dp))
        Text(label, style = Type.small)
    }
}

/** A peak with a flag: the streak mark used across the app. */
@Composable
fun PeakGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.06f, h * 0.92f)
            lineTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.60f, h * 0.58f)
            lineTo(w * 0.72f, h * 0.44f)
            lineTo(w * 0.96f, h * 0.92f)
            close()
        }
        drawPath(p, color)
        drawLine(color, Offset(w * 0.42f, h * 0.30f), Offset(w * 0.42f, h * 0.04f), strokeWidth = w * 0.07f)
        val flag = Path().apply {
            moveTo(w * 0.42f, h * 0.04f)
            lineTo(w * 0.66f, h * 0.12f)
            lineTo(w * 0.42f, h * 0.20f)
            close()
        }
        drawPath(flag, color)
    }
}

enum class Glyph { TODAY, PLAN, CLIMB, INSIGHTS, SETTINGS }

@Composable
fun TabGlyph(g: Glyph, color: Color, size: Dp = 22.dp) {
    if (g == Glyph.CLIMB) {
        PeakGlyph(size, color)
        return
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.085f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (g) {
            Glyph.TODAY -> {
                drawCircle(color, w * 0.42f, center, style = st)
                drawLine(color, center, Offset(center.x, w * 0.24f), sw, StrokeCap.Round)
                drawLine(color, center, Offset(w * 0.68f, center.y), sw, StrokeCap.Round)
            }
            Glyph.PLAN -> {
                listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                    drawCircle(color, sw * 0.8f, Offset(w * 0.14f, w * y))
                    drawLine(color, Offset(w * 0.32f, w * y), Offset(w * 0.9f, w * y), sw, StrokeCap.Round)
                }
            }
            Glyph.INSIGHTS -> {
                listOf(0.2f to 0.55f, 0.5f to 0.25f, 0.8f to 0.45f).forEach { (x, top) ->
                    drawLine(color, Offset(w * x, w * 0.88f), Offset(w * x, w * top), sw * 1.5f, StrokeCap.Round)
                }
            }
            Glyph.SETTINGS -> {
                drawCircle(color, w * 0.16f, center, style = st)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val r1 = w * 0.3f
                    val r2 = w * 0.44f
                    drawLine(
                        color,
                        Offset(center.x + (r1 * Math.cos(a)).toFloat(), center.y + (r1 * Math.sin(a)).toFloat()),
                        Offset(center.x + (r2 * Math.cos(a)).toFloat(), center.y + (r2 * Math.sin(a)).toFloat()),
                        sw, StrokeCap.Round,
                    )
                }
            }
            Glyph.CLIMB -> Unit
        }
    }
}

/** The screen shown right after a task is checked out. */
@Composable
fun ResultView(task: Task, result: TaskResult, onClose: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(result.checkedOutAt) {
        anim.snapTo(0f)
        anim.animateTo(result.efficiency / 100f, tween(1100, easing = FastOutSlowInEasing))
    }
    val shown = (anim.value * 100).toInt()
    val c = if (result.outcome == Outcome.MISSED) Ink.faint else effColor(result.efficiency)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(if (result.outcome == Outcome.MISSED) "Logged as missed" else "Task complete", color = c)
        Spacer(Modifier.height(8.dp))
        Text(task.title, style = Type.h2, textAlign = TextAlign.Center)
        Text(
            "${Messages.time(task.startMin)} – ${Messages.time(task.endMin)} · ${task.category.label}",
            style = Type.small,
        )
        Spacer(Modifier.height(28.dp))
        Ring(anim.value, 188.dp, 12.dp, c) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$shown%", style = Type.bigNum.copy(color = c))
                Text("efficiency", style = Type.small)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(Messages.resultLine(result, task.category), style = Type.body.copy(color = Ink.text), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (result.unlocks != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("${result.unlocks}", "unlocks")
                Metric("${result.distractMin ?: 0}m", "distracting apps", color = if ((result.distractMin ?: 0) > 0) Ink.bad else Ink.text)
                Metric("${result.screenMin ?: 0}m", "screen on")
            }
            if (result.distractApps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth()) {
                    Label("Where the time went")
                    Spacer(Modifier.height(8.dp))
                    val max = result.distractApps.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    result.distractApps.entries.take(4).forEach { (app, min) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(96.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ink.raised)) {
                                Box(Modifier.fillMaxWidth(min / max.toFloat()).height(6.dp).background(Ink.bad))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${min}m", style = Type.num.copy(color = Ink.dim))
                        }
                    }
                }
            }
        } else {
            Text("Phone stats are off. Allow usage access in Settings to see unlocks and app time.", style = Type.small, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("+${result.points}", "points", color = Ink.amber)
            Metric(Messages.duration(result.measuredMin), if (result.endedEarly) "measured (ended early)" else "measured")
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Close", onClick = onClose)
    }
}
