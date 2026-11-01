@file:OptIn(ExperimentalMaterial3Api::class)

package com.agnibha.tracker.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Category
import com.agnibha.tracker.R

/** Night-sky ink with one warm accent. Pixel art supplies the rest of the colour. */
object Ink {
    val bg = Color(0xFF0E1016)
    val surface = Color(0xFF171A23)
    val raised = Color(0xFF20242F)
    val line = Color(0xFF2B3040)
    val text = Color(0xFFECE8E1)
    val dim = Color(0xFF9AA0AA)
    val faint = Color(0xFF5F6672)
    val amber = Color(0xFFF0A23B)
    val amberDeep = Color(0xFFB8741F)
    val amberSoft = Color(0x29F0A23B)
    val good = Color(0xFF6FBF8B)
    val bad = Color(0xFFE0745A)
    val sky = Color(0xFF7FA7E8)
}

fun Category.tint(): Color = Color(color)

fun Category.sprite(): Sprite = when (this) {
    Category.STUDY -> Sprites.catStudy
    Category.CLASS -> Sprites.catClass
    Category.LAB -> Sprites.catLab
    Category.PROJECT -> Sprites.catProject
    Category.WORKOUT -> Sprites.catWorkout
    Category.MUSIC -> Sprites.catMusic
    Category.ERRAND -> Sprites.catErrand
    Category.BREAK -> Sprites.catBreak
}

fun mealSprite(m: com.agnibha.tracker.Meal): Sprite = when (m) {
    com.agnibha.tracker.Meal.BREAKFAST -> Sprites.mealBreakfast
    com.agnibha.tracker.Meal.LUNCH -> Sprites.mealLunch
    com.agnibha.tracker.Meal.DINNER -> Sprites.mealDinner
}

fun effColor(e: Int): Color = when {
    e >= 80 -> Ink.good
    e >= 55 -> Ink.amber
    else -> Ink.bad
}

/** One pixel face for everything: headings, numbers, labels and running text. */
val Pix = FontFamily(
    Font(R.font.pixelify, FontWeight.Normal),
    Font(R.font.pixelify, FontWeight.Medium),
    Font(R.font.pixelify, FontWeight.SemiBold),
    Font(R.font.pixelify, FontWeight.Bold),
)

val Sans = Pix

object Type {
    val hero = TextStyle(fontFamily = Pix, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, color = Ink.text)
    val h1 = TextStyle(fontFamily = Pix, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, color = Ink.text)
    val h2 = TextStyle(fontFamily = Pix, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 26.sp, color = Ink.text)
    val title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, color = Ink.text)
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, color = Ink.dim)
    val small = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp, color = Ink.dim)
    val label = TextStyle(fontFamily = Pix, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp, color = Ink.faint)
    val num = TextStyle(fontFamily = Pix, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink.text)
    val bigNum = TextStyle(fontFamily = Pix, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 50.sp, color = Ink.text)
}

@Composable
fun DaybookTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Ink.amber,
        onPrimary = Ink.bg,
        primaryContainer = Ink.amberSoft,
        onPrimaryContainer = Ink.amber,
        secondary = Ink.dim,
        onSecondary = Ink.bg,
        tertiary = Ink.good,
        background = Ink.bg,
        onBackground = Ink.text,
        surface = Ink.surface,
        onSurface = Ink.text,
        surfaceVariant = Ink.raised,
        onSurfaceVariant = Ink.dim,
        surfaceContainer = Ink.surface,
        surfaceContainerLow = Ink.surface,
        surfaceContainerHigh = Ink.raised,
        surfaceContainerHighest = Ink.raised,
        outline = Ink.line,
        outlineVariant = Ink.line,
        error = Ink.bad,
        scrim = Color(0xCC07080B),
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Pix),
        displayMedium = base.displayMedium.copy(fontFamily = Pix),
        displaySmall = base.displaySmall.copy(fontFamily = Pix),
        headlineLarge = base.headlineLarge.copy(fontFamily = Pix),
        headlineMedium = base.headlineMedium.copy(fontFamily = Pix),
        headlineSmall = base.headlineSmall.copy(fontFamily = Pix),
        titleLarge = base.titleLarge.copy(fontFamily = Sans),
        titleMedium = base.titleMedium.copy(fontFamily = Sans),
        titleSmall = base.titleSmall.copy(fontFamily = Sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = Sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
        bodySmall = base.bodySmall.copy(fontFamily = Sans),
        labelLarge = base.labelLarge.copy(fontFamily = Pix),
        labelMedium = base.labelMedium.copy(fontFamily = Pix),
        labelSmall = base.labelSmall.copy(fontFamily = Pix),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides PixelIndication,
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}

/** Press feedback as a checkerboard dither of light pixels, instead of a round ripple. */
object PixelIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PixelIndicationNode(interactionSource)
    override fun hashCode(): Int = 7
    override fun equals(other: Any?): Boolean = other === this
}

private class PixelIndicationNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> { pressed = true; invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> { pressed = false; invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pressed) return
        val px = 3.dp.toPx()
        val cols = (size.width / px).toInt()
        val rows = (size.height / px).toInt()
        for (r in 1 until rows - 1) for (c in 1 until cols - 1) {
            if ((r + c) % 2 == 0) drawRect(Color.White, Offset(c * px, r * px), Size(px, px), alpha = 0.07f)
        }
    }
}
