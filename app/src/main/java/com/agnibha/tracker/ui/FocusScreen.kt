package com.agnibha.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Store
import com.agnibha.tracker.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

/** Full-screen timer for one task: a pixel candle that burns down as the block runs. */
@Composable
fun FocusScreen(dateKey: String, taskId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val task = Store.findTask(dateKey, taskId)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var flame by remember { mutableIntStateOf(0) }
    var window by remember { mutableStateOf<Usage.Window?>(null) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) { while (true) { delay(170); flame++ } }

    if (task == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val start = Clock.epoch(dateKey, task.startMin)
    val end = Clock.epoch(dateKey, task.endMin)
    LaunchedEffect(taskId) {
        while (true) {
            val t = System.currentTimeMillis()
            if (t > start) {
                window = withContext(Dispatchers.IO) { Usage.measure(ctx, start, minOf(t, end), Store.state.distracting) }
            }
            delay(30_000)
        }
    }

    val before = now < start
    val over = now >= end
    val remaining = if (before) end - start else (end - now).coerceAtLeast(0)
    val frac = (remaining.toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    val shown = if (before) start - now else remaining
    val secs = (shown / 1000).toInt()
    val clock = if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60) else "%02d:%02d".format(secs / 60, secs % 60)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(task.category.sprite(), Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Label("Focus · ${task.category.label}", color = task.category.tint(), modifier = Modifier.weight(1f))
            Text("Leave", style = Type.num.copy(color = Ink.dim), modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(task.title, style = Type.h1, textAlign = TextAlign.Center)
        Text("${Messages.time(task.startMin)} – ${Messages.time(task.endMin)}", style = Type.small)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Candle(frac, flame, lit = !over, modifier = Modifier.size(width = 160.dp, height = 260.dp))
        }

        Text(
            when {
                before -> "Starts in"
                over -> "Time's up"
                else -> "Left"
            },
            style = Type.label,
        )
        Text(if (over) "00:00" else clock, style = Type.bigNum.copy(fontSize = 64.sp, lineHeight = 66.sp, color = if (over) Ink.amber else Ink.text))
        Spacer(Modifier.height(12.dp))
        val w = window
        if (!before) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric(w?.unlocks?.toString() ?: "–", "unlocks", color = if ((w?.unlocks ?: 0) > 2) Ink.bad else Ink.text)
                Metric(w?.distractMin?.let { "${it}m" } ?: "–", "distracting", color = if ((w?.distractMin ?: 0) > 0) Ink.bad else Ink.text)
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(Messages.focusLine(task.category, w?.unlocks), style = Type.small, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        when {
            over -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    PrimaryButton("Done") {
                        Store.checkOut(ctx, dateKey, taskId, Outcome.DONE, false)?.let { r -> onClose(); Nav.showResult(dateKey, taskId, r) }
                    }
                }
                GhostButton("Partly", Modifier.weight(1f).height(52.dp)) {
                    Store.checkOut(ctx, dateKey, taskId, Outcome.PARTIAL, false)?.let { r -> onClose(); Nav.showResult(dateKey, taskId, r) }
                }
            }
            !before -> PrimaryButton("Finished early") {
                Store.checkOut(ctx, dateKey, taskId, Outcome.DONE, true)?.let { r -> onClose(); Nav.showResult(dateKey, taskId, r) }
            }
            else -> GhostButton("Back", Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

/** A candle drawn in blocks. Its height is the time left; the flame flickers through three frames. */
@Composable
private fun Candle(fraction: Float, frame: Int, lit: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val px = floor(size.width / 20f)
        val cols = 20
        val maxRows = ((size.height / px).toInt() - 14).coerceAtLeast(6)
        val rows = (fraction * (maxRows - 2)).roundToInt() + 2
        val ox = floor((size.width - cols * px) / 2f)
        val baseY = size.height - 3 * px

        // Holder
        for (x in 2 until cols - 2) drawRect(Ink.dim, Offset(ox + x * px, baseY), Size(px, px))
        for (x in 0 until cols) drawRect(Ink.line, Offset(ox + x * px, baseY + px), Size(px, px * 2))

        // Wax
        val left = 6
        val right = 14
        val topY = baseY - rows * px
        for (y in 0 until rows) {
            for (x in left until right) {
                val c = when (x) {
                    left -> Ink.dim
                    right - 1 -> Ink.faint
                    else -> Ink.text
                }
                drawRect(c, Offset(ox + x * px, topY + y * px), Size(px, px))
            }
        }
        // Drip
        if (rows > 4) {
            drawRect(Ink.text, Offset(ox + (right) * px, topY + 2 * px), Size(px, px * 2))
            drawRect(Ink.text, Offset(ox + (left - 1) * px, topY + px), Size(px, px))
        }
        // Wick
        drawRect(Ink.faint, Offset(ox + 9 * px, topY - px), Size(px, px))

        if (lit) {
            // Glow: three dithered squares of light around the flame
            val cx = ox + 9.5f * px
            val cy = topY - 5 * px
            listOf(9 to 0.05f, 6 to 0.08f, 4 to 0.12f).forEach { (r, a) ->
                drawRect(Ink.amber, Offset(cx - r * px, cy - r * px), Size(2 * r * px, 2 * r * px), alpha = a)
            }
            val sp = when (frame % 3) { 0 -> Sprites.flameA; 1 -> Sprites.flameB; else -> Sprites.flameC }
            drawSprite(sp, Offset(ox + 7 * px, topY - 9 * px), px)
        }
    }
}
