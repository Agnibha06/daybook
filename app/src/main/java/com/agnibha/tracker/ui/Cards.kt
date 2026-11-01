package com.agnibha.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The hard drop shadow every card casts. */
val CardShadow = Color(0xFF07080B)

/** Light top-left edge and dark bottom-right edge, following the stepped corners. */
fun Modifier.bevel(step: Dp = 3.dp, light: Color = Color.White.copy(alpha = 0.07f), dark: Color = Color.Black.copy(alpha = 0.3f)): Modifier =
    drawBehind {
        val s = step.toPx()
        val t = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawRect(light, Offset(2 * s, 0f), Size(w - 4 * s, t))
        drawRect(light, Offset(0f, 2 * s), Size(t, h - 4 * s))
        drawRect(light, Offset(s, s), Size(s, t))
        drawRect(light, Offset(s, s), Size(t, s))
        drawRect(dark, Offset(2 * s, h - t), Size(w - 4 * s, t))
        drawRect(dark, Offset(w - t, 2 * s), Size(t, h - 4 * s))
        drawRect(dark, Offset(w - 2 * s, h - s - t), Size(s, t))
        drawRect(dark, Offset(w - s - t, h - 2 * s), Size(t, s))
    }

/**
 * A raised pixel surface: hard shadow, bevel, and a press that sinks it into its shadow.
 * Everything card-like in the app is built on this.
 */
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    color: Color = Ink.surface,
    border: Color? = null,
    depth: Dp = 4.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "press",
    )
    Box(modifier.padding(end = depth, bottom = depth)) {
        Box(Modifier.matchParentSize().offset(depth, depth).background(CardShadow, PxShape))
        var body = Modifier
            .graphicsLayer {
                translationX = press * (depth.toPx() - 1f)
                translationY = press * (depth.toPx() - 1f)
            }
            .background(color, PxShape)
            .bevel()
        if (border != null) body = body.border(2.dp, border, PxShape)
        if (onClick != null) body = body.clickable(interactionSource = interaction, indication = PixelIndication, onClick = onClick)
        Box(body.padding(padding), content = content)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ink.surface,
    border: Color? = null,
    tab: String? = null,
    tabColor: Color = border ?: Ink.amber,
    sprite: Sprite? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = if (tab != null || sprite != null) 16.dp else 0.dp
    Box(modifier.fillMaxWidth().padding(top = top)) {
        PixelBox(
            Modifier.fillMaxWidth(),
            color = color,
            border = border,
            padding = PaddingValues(start = 16.dp, end = 16.dp, top = if (tab != null) 20.dp else 16.dp, bottom = 16.dp),
            onClick = onClick,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (tab != null) {
            Box(
                Modifier
                    .offset(x = 14.dp, y = (-11).dp)
                    .background(tabColor, PxShapeSmall)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(tab.uppercase(), style = Type.label.copy(color = Ink.bg))
            }
        }
        if (sprite != null) Peek(sprite, Modifier.align(Alignment.TopEnd).offset(x = (-18).dp, y = (-18).dp))
    }
}

/** A sprite that sits on a card's top edge and bobs gently. */
@Composable
fun Peek(sprite: Sprite, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val bob by rememberInfiniteTransition(label = "peek").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    PixelSprite(sprite, modifier.size(size).graphicsLayer { translationY = -bob * 3.dp.toPx() })
}

/** Slides and fades an element in when it first appears, [index] steps after the first. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index.coerceAtMost(12))
        a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 26.dp.toPx()
        scaleX = 0.96f + 0.04f * v
        scaleY = 0.96f + 0.04f * v
    }
}

/** Text whose leading number counts up to its value, e.g. "42%", "+18", "6h 30m". */
@Composable
fun CountText(value: String, style: TextStyle, modifier: Modifier = Modifier) {
    val m = remember(value) { Regex("^([+]?)(\\d{1,6})(.*)$").find(value) }
    if (m == null) {
        Text(value, style = style, modifier = modifier)
        return
    }
    val target = m.groupValues[2].toInt()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(750, easing = FastOutSlowInEasing)) }
    Text(m.groupValues[1] + anim.value.roundToInt() + m.groupValues[3], style = style, modifier = modifier)
}

/** The climber says something: a pixel speech bubble with a stepped tail. */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    fromRight: Boolean = false,
    speaker: Sprite? = null,
    color: Color = Ink.raised,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var frame by remember { mutableIntStateOf(0) }
    if (speaker == Sprites.climberA) LaunchedEffect(Unit) { while (true) { delay(420); frame++ } }
    val who = if (speaker == Sprites.climberA && frame % 2 == 1) Sprites.climberB else speaker
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (!fromRight && who != null) {
            PixelSprite(who, Modifier.size(40.dp))
            Spacer(Modifier.width(2.dp))
        }
        if (!fromRight) Tail(color, pointLeft = true)
        Column(
            Modifier
                .weight(1f)
                .background(color, PxShape)
                .bevel()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (text.isNotEmpty()) Text(text, style = Type.body.copy(color = Ink.text))
            content?.invoke(this)
        }
        if (fromRight) Tail(color, pointLeft = false)
        if (fromRight && who != null) {
            Spacer(Modifier.width(2.dp))
            PixelSprite(who, Modifier.size(40.dp), flipX = true)
        }
    }
}

@Composable
private fun Tail(color: Color, pointLeft: Boolean) {
    Canvas(Modifier.size(width = 9.dp, height = 18.dp)) {
        val p = 3.dp.toPx()
        // Three steps, widest against the bubble.
        for (i in 0 until 3) {
            val w = (i + 1) * p
            val x = if (pointLeft) size.width - w else 0f
            drawRect(color, Offset(x, size.height - (3 - i) * p - p * 0), Size(w, p))
        }
    }
}

/** A zig-zag of blocks across the page, with an optional sprite resting in the middle. */
@Composable
fun PixelDivider(sprite: Sprite? = null, color: Color = Ink.line) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val p = 3.dp.toPx()
            val n = (size.width / p).toInt()
            val mid = n / 2
            for (i in 0 until n) {
                if (sprite != null && kotlin.math.abs(i - mid) < 8) continue
                val y = when (i % 4) { 0 -> 0; 1, 3 -> 1; else -> 2 } * p
                if (i % 2 == 0) drawRect(color, Offset(i * p, y + p), Size(p, p))
            }
        }
        if (sprite != null) Peek(sprite, size = 24.dp)
    }
}
