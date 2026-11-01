package com.agnibha.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min

/** A grid of characters, one per pixel; '.' is transparent. Colours come from [PixelPalette]. */
class Sprite(vararg val rows: String) {
    val w: Int = rows.first().length
    val h: Int = rows.size

    class Run(val x: Int, val y: Int, val len: Int, val color: Color, val muted: Color)

    /** Horizontal runs of the same colour, so each row draws in a few rectangles. */
    val runs: List<Run> = buildList {
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                val ch = row[x]
                if (ch == '.') { x++; continue }
                var len = 1
                while (x + len < row.length && row[x + len] == ch) len++
                val c = Color(PixelPalette.colors[ch] ?: 0xFFFF00FF)
                add(Run(x, y, len, c, mute(c)))
                x += len
            }
        }
    }

    private fun mute(c: Color): Color {
        val lum = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
        val v = 0.22f + lum * 0.38f
        return Color(v * 0.95f, v * 0.97f, v * 1.06f, c.alpha)
    }
}

/**
 * Draw a sprite with its top-left at [topLeft], [cell] pixels per sprite pixel.
 * Edges are floored to whole screen pixels so neighbouring blocks never leave seams.
 */
fun DrawScope.drawSprite(
    sprite: Sprite,
    topLeft: Offset,
    cell: Float,
    muted: Boolean = false,
    alpha: Float = 1f,
    flipX: Boolean = false,
    tint: Color? = null,
) {
    for (r in sprite.runs) {
        val sx = if (flipX) sprite.w - r.x - r.len else r.x
        val l = floor(topLeft.x + sx * cell)
        val rgt = floor(topLeft.x + (sx + r.len) * cell)
        val t = floor(topLeft.y + r.y * cell)
        val b = floor(topLeft.y + (r.y + 1) * cell)
        drawRect(tint ?: if (muted) r.muted else r.color, Offset(l, t), Size(rgt - l, b - t), alpha = alpha)
    }
}

/** A sprite sized to fit its box, using a whole number of screen pixels per sprite pixel. */
@Composable
fun PixelSprite(
    sprite: Sprite,
    modifier: Modifier,
    muted: Boolean = false,
    alpha: Float = 1f,
    flipX: Boolean = false,
    tint: Color? = null,
) {
    Canvas(modifier) {
        val cell = floor(min(size.width / sprite.w, size.height / sprite.h)).coerceAtLeast(1f)
        val ox = floor((size.width - cell * sprite.w) / 2f)
        val oy = floor((size.height - cell * sprite.h) / 2f)
        drawSprite(sprite, Offset(ox, oy), cell, muted, alpha, flipX, tint)
    }
}

/** Rectangle with stepped corners, two steps of [step] each: the pixel-art rounded corner. */
class PixelShape(private val step: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }.coerceAtMost(min(size.width, size.height) / 4f)
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(2 * s, 0f)
            lineTo(w - 2 * s, 0f); lineTo(w - 2 * s, s); lineTo(w - s, s); lineTo(w - s, 2 * s); lineTo(w, 2 * s)
            lineTo(w, h - 2 * s); lineTo(w - s, h - 2 * s); lineTo(w - s, h - s); lineTo(w - 2 * s, h - s); lineTo(w - 2 * s, h)
            lineTo(2 * s, h); lineTo(2 * s, h - s); lineTo(s, h - s); lineTo(s, h - 2 * s); lineTo(0f, h - 2 * s)
            lineTo(0f, 2 * s); lineTo(s, 2 * s); lineTo(s, s); lineTo(2 * s, s)
            close()
        }
        return Outline.Generic(p)
    }
}

val PxShape = PixelShape(3.dp)
val PxShapeSmall = PixelShape(2.dp)
