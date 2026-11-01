package com.agnibha.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Task
import com.agnibha.tracker.TaskResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** One-line messages that slide up from the bottom of the app. */
object Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    var serial by mutableStateOf(0)
        private set

    fun show(text: String) {
        message = text
        serial++
    }

    fun clear() { message = null }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ink.faint) {
    Text(text.uppercase(), style = Type.label.copy(color = color), modifier = modifier)
}

/** Chunky button with a pixel drop-shadow that sinks when pressed. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink.amber,
    shadow: Color = Ink.amberDeep,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed && enabled) 4.dp else 0.dp, tween(60), label = "press")
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Box(
            Modifier.fillMaxWidth().height(52.dp).offset(y = 4.dp)
                .background(if (enabled) shadow else Ink.bg, PxShape),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .offset(y = drop)
                .background(if (enabled) color else Ink.raised, PxShape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = Type.num.copy(fontSize = 17.sp, color = if (enabled) Ink.bg else Ink.faint))
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.text,
    enabled: Boolean = true,
    icon: Sprite? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(48.dp)
            .background(if (pressed) Ink.raised else Color.Transparent, PxShape)
            .border(2.dp, if (enabled) Ink.line else Ink.surface, PxShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            PixelSprite(icon, Modifier.size(14.dp), tint = if (enabled) color else Ink.faint)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = Type.title.copy(color = if (enabled) color else Ink.faint), textAlign = TextAlign.Center)
    }
}

/** Square icon button: a sprite on a raised pixel tile. */
@Composable
fun PixelIconButton(sprite: Sprite, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .background(Ink.surface, PxShapeSmall)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelSprite(sprite, Modifier.size(18.dp), muted = !enabled, tint = if (enabled) tint else null)
    }
}

/** On/off switch drawn as a block that jumps between two cells. */
@Composable
fun PixelToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by androidx.compose.animation.core.animateDpAsState(if (checked) 22.dp else 0.dp, tween(120), label = "toggle")
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .background(if (checked) Ink.amberSoft else Ink.raised, PxShapeSmall)
            .border(2.dp, if (checked) Ink.amber else Ink.line, PxShapeSmall)
            .clickable { onChange(!checked) }
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).background(if (checked) Ink.amber else Ink.dim, PxShapeSmall))
    }
}

/** Single- or multi-line text input in a pixel frame. */
@Composable
fun PixelTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = Type.title,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.amber),
        interactionSource = interaction,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = capitalization),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.bg, PxShapeSmall)
                    .border(2.dp, if (focused) Ink.amber else Ink.line, PxShapeSmall)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = Type.title.copy(color = Ink.faint))
                inner()
            }
        },
    )
}

/** Time picker made of pixel steppers: hours, minutes in 5s, and AM/PM. */
@Composable
fun PixelTimeDialog(initialMin: Int, title: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var min by remember { mutableStateOf(((initialMin % 1440) + 1440) % 1440) }
    val h24 = min / 60
    val m = min % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    val pm = h24 >= 12
    fun add(delta: Int) { min = ((min + delta) % 1440 + 1440) % 1440 }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Ink.surface, PxShape).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = Type.h2)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("${h12}", onUp = { add(60) }, onDown = { add(-60) })
                Text(":", style = Type.bigNum.copy(color = Ink.dim), modifier = Modifier.padding(horizontal = 6.dp))
                Stepper(m.toString().padStart(2, '0'), onUp = { add(5 - m % 5) }, onDown = { add(if (m % 5 == 0) -5 else -(m % 5)) })
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("AM", !pm) { if (pm) add(-720) }
                    Chip("PM", pm) { if (!pm) add(720) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-30, -15, 15, 30).forEach { d ->
                    Chip(if (d > 0) "+${d}m" else "${d}m", false, Ink.dim) { add(d) }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Set ${Messages.time(min)}") { onPick(min) }
            Spacer(Modifier.height(6.dp))
            Text("Cancel", style = Type.num.copy(color = Ink.dim), modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
        }
    }
}

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PixelIconButton(Sprites.arrowUp, onClick = onUp)
        Box(Modifier.padding(vertical = 6.dp).width(72.dp).background(Ink.bg, PxShapeSmall).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(value, style = Type.bigNum)
        }
        PixelIconButton(Sprites.arrowDown, onClick = onDown)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = Ink.amber, sprite: Sprite? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 700f),
        label = "chip",
    )
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (selected) color.copy(alpha = 0.16f) else Ink.raised, PxShapeSmall)
            .border(2.dp, if (selected) color else Color.Transparent, PxShapeSmall)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sprite != null) {
            PixelSprite(sprite, Modifier.size(18.dp), muted = !selected)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.small.copy(color = if (selected) color else Ink.dim))
    }
}

/** A square block, the pixel stand-in for a dot. */
@Composable
fun Block(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color))
}

/** Progress drawn as square blocks around a circle. */
@Composable
fun PixelDial(
    progress: Float,
    size: Dp,
    color: Color,
    blocks: Int = 32,
    track: Color = Ink.line,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val sq = floor((2 * PI * r / blocks * 0.62).toFloat()).coerceAtLeast(2f)
            val rr = r - sq
            val filled = (progress.coerceIn(0f, 1f) * blocks).toInt()
            for (i in 0 until blocks) {
                val a = -PI / 2 + 2 * PI * i / blocks
                val cx = center.x + (rr * cos(a)).toFloat()
                val cy = center.y + (rr * sin(a)).toFloat()
                drawRect(
                    if (i < filled) color else track,
                    Offset(floor(cx - sq / 2), floor(cy - sq / 2)),
                    Size(sq, sq),
                )
            }
        }
        content()
    }
}

/** A row of blocks, filled from the left. */
@Composable
fun PixelMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink.amber,
    blocks: Int = 20,
    height: Dp = 10.dp,
    track: Color = Ink.raised,
    ghost: Float = 0f,
    ghostColor: Color = color.copy(alpha = 0.28f),
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (blocks - 1)) / blocks
        val filled = Math.round(fraction.coerceIn(0f, 1f) * blocks)
        val ghosted = Math.round(ghost.coerceIn(0f, 1f) * blocks)
        for (i in 0 until blocks) {
            val x = floor(i * (w + gap))
            val c = when {
                i < filled -> color
                i < ghosted -> ghostColor
                else -> track
            }
            drawRect(c, Offset(x, 0f), Size(floor(w), size.height))
        }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink.text) {
    Column(modifier) {
        CountText(value, Type.num.copy(fontSize = 22.sp, color = color))
        Spacer(Modifier.height(2.dp))
        Text(label, style = Type.small)
    }
}

@Composable
fun CategoryBadge(task: Task, size: Dp = 36.dp, done: Boolean = false) {
    Box(
        Modifier.size(size).background(task.category.tint().copy(alpha = if (done) 0.08f else 0.16f), PxShapeSmall),
        contentAlignment = Alignment.Center,
    ) {
        PixelSprite(task.category.sprite(), Modifier.size(size * 0.62f), muted = done)
    }
}

/** The screen shown right after a task is checked out. */
@Composable
fun ResultView(task: Task, result: TaskResult, onClose: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(result.checkedOutAt) {
        anim.snapTo(0f)
        anim.animateTo(result.efficiency / 100f, tween(1200, easing = FastOutSlowInEasing))
    }
    val shown = (anim.value * 100).toInt()
    val c = if (result.outcome == Outcome.MISSED) Ink.faint else effColor(result.efficiency)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryBadge(task, 48.dp)
        Spacer(Modifier.height(12.dp))
        Label(if (result.outcome == Outcome.MISSED) "Logged as missed" else "Task complete", color = c)
        Spacer(Modifier.height(6.dp))
        Text(task.title, style = Type.h2, textAlign = TextAlign.Center)
        Text(
            "${Messages.time(task.startMin)} – ${Messages.time(task.endMin)} · ${task.category.label}",
            style = Type.small,
        )
        Spacer(Modifier.height(24.dp))
        PixelDial(anim.value, 196.dp, c, blocks = 40) {
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
                Metric("${result.distractMin ?: 0}m", "distracting", color = if ((result.distractMin ?: 0) > 0) Ink.bad else Ink.text)
                Metric("${result.screenMin ?: 0}m", "screen on")
            }
            if (result.distractApps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth()) {
                    Label("Where the time went")
                    Spacer(Modifier.height(10.dp))
                    val max = result.distractApps.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    result.distractApps.entries.take(4).forEach { (app, min) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(96.dp), maxLines = 1)
                            PixelMeter(min / max.toFloat(), Modifier.weight(1f), Ink.bad, blocks = 14, height = 8.dp)
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
            Metric(Messages.duration(result.measuredMin), if (result.endedEarly) "measured, ended early" else "measured")
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Close", onClick = onClose)
    }
}
