package com.agnibha.tracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Category
import com.agnibha.tracker.R

/** Blue-graphite night with one warm accent: a desk lamp at 2 AM. */
object Ink {
    val bg = Color(0xFF101318)
    val surface = Color(0xFF171B22)
    val raised = Color(0xFF1F242D)
    val line = Color(0xFF2A303A)
    val text = Color(0xFFECE8E1)
    val dim = Color(0xFF9AA0AA)
    val faint = Color(0xFF5F6672)
    val amber = Color(0xFFF0A23B)
    val amberSoft = Color(0x29F0A23B)
    val good = Color(0xFF6FBF8B)
    val bad = Color(0xFFE0745A)
}

fun Category.tint(): Color = Color(color)

fun effColor(e: Int): Color = when {
    e >= 80 -> Ink.good
    e >= 55 -> Ink.amber
    else -> Ink.bad
}

val Sans = FontFamily(
    Font(R.font.plex_sans, FontWeight.Normal),
    Font(R.font.plex_sans, FontWeight.Medium),
    Font(R.font.plex_sans, FontWeight.SemiBold),
    Font(R.font.plex_sans, FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.plex_mono_semibold, FontWeight.Normal),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

object Type {
    val hero = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, color = Ink.text)
    val h1 = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, color = Ink.text)
    val h2 = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = Ink.text)
    val title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = Ink.text)
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.dim)
    val small = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Ink.dim)
    val label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Ink.faint)
    val num = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink.text)
    val bigNum = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp, color = Ink.text)
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
        surfaceContainerHigh = Ink.raised,
        surfaceContainerHighest = Ink.raised,
        outline = Ink.line,
        outlineVariant = Ink.line,
        error = Ink.bad,
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Mono),
        displayMedium = base.displayMedium.copy(fontFamily = Mono),
        displaySmall = base.displaySmall.copy(fontFamily = Mono),
        headlineLarge = base.headlineLarge.copy(fontFamily = Sans),
        headlineMedium = base.headlineMedium.copy(fontFamily = Sans),
        headlineSmall = base.headlineSmall.copy(fontFamily = Sans),
        titleLarge = base.titleLarge.copy(fontFamily = Sans),
        titleMedium = base.titleMedium.copy(fontFamily = Sans),
        titleSmall = base.titleSmall.copy(fontFamily = Sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = Sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
        bodySmall = base.bodySmall.copy(fontFamily = Sans),
        labelLarge = base.labelLarge.copy(fontFamily = Sans),
        labelMedium = base.labelMedium.copy(fontFamily = Sans),
        labelSmall = base.labelSmall.copy(fontFamily = Sans),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
