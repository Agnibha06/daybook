package com.agnibha.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Insights
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun InsightsScreen() {
    val s = Store.state
    val sum = Insights.summary(s, 7)
    val days = Insights.daily(s, 14)
    val hours = Insights.byHour(s, 28)
    val cats = Insights.categories(s, 7)
    val apps = Insights.apps(s, 7)
    val facts = Insights.facts(s)
    val nights = Insights.sleep(s, 14)
    val hasData = sum.checked > 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("Last 7 days")
        Text("Stats", style = Type.h1)

        if (!hasData) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.tabInsights, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Your numbers show up here", style = Type.title)
                        Text(
                            "Check out a few tasks and this page fills with completion, efficiency by hour, the apps that cost you focus, and patterns in how you work.",
                            style = Type.small,
                        )
                    }
                }
            }
        }

        // Bento: big completion dial beside two stacked tiles, then the mirror image below.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).appear(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelBox(Modifier.weight(1f).fillMaxHeight(), color = Ink.raised) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("Completion")
                    Spacer(Modifier.height(10.dp))
                    PixelDial((sum.completion ?: 0) / 100f, 112.dp, sum.completion?.let { effColor(it) } ?: Ink.line, blocks = 28) {
                        CountText(sum.completion?.let { "$it%" } ?: "–", Type.h1.copy(color = sum.completion?.let { effColor(it) } ?: Ink.dim))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("of tasks due", style = Type.small)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tile(Sprites.badgeDeep, "Efficiency", sum.avgEff?.let { "$it%" } ?: "–", "average per task", Modifier.fillMaxWidth(), sum.avgEff?.let { effColor(it) })
                Tile(Sprites.catStudy, "Focus", if (sum.focusMin > 0) Messages.duration(sum.focusMin) else "–", "deep-work time", Modifier.fillMaxWidth(), null)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).appear(2), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tile(Sprites.bed, "Sleep", sum.avgSleep?.let { Messages.duration(it) } ?: "–", "per night", Modifier.fillMaxWidth(),
                    sum.avgSleep?.let { if (it >= 7 * 60) Ink.good else if (it >= 6 * 60) Ink.amber else Ink.bad })
                Tile(Sprites.alarm, "Snoozes", "${sum.snoozes}", "this week", Modifier.fillMaxWidth(), if (sum.snoozes == 0) Ink.good else null)
            }
            PixelBox(Modifier.weight(1f).fillMaxHeight(), color = Ink.raised) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelSprite(Sprites.badgeLock, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Label("Locked in")
                    }
                    Spacer(Modifier.height(10.dp))
                    CountText("${sum.daysLocked}/7", Type.h1.copy(color = if (sum.daysLocked >= 6) Ink.good else Ink.text))
                    Text("nights before 3 AM", style = Type.small)
                    Spacer(Modifier.height(12.dp))
                    // The last seven nights as seven blocks, oldest first.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.takeLast(7).forEach { d -> Block(if (d.locked) Ink.amber else Ink.line, 14.dp) }
                    }
                }
            }
        }
        Row(Modifier.appear(3), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(Sprites.badgeGhost, "Per unlock", sum.minPerUnlock?.let { "${it}m" } ?: "–", "focus between unlocks", Modifier.weight(1f), null)
            Tile(Sprites.badgeSteady, "Moves", "${sum.movesUsed}", "of ${7 * com.agnibha.tracker.MAX_MOVES} used", Modifier.weight(1f), null)
        }

        // Sleep: 14 nights as columns of half-hour blocks, with a marker at the planned amount.
        Panel(Modifier.appear(4), tab = "Sleep", tabColor = Ink.sky, sprite = Sprites.zzz) {
            Text("Hours slept each night. Amber block = what you planned.", style = Type.small)
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val n = nights.size
                val levels = 20 // half hours, up to 10 h
                val gapX = 5.dp.toPx()
                val gapY = 1.dp.toPx()
                val bw = floor((size.width - gapX * (n - 1)) / n)
                val bh = floor((size.height - gapY * (levels - 1)) / levels)
                nights.forEachIndexed { i, (_, slept, planned) ->
                    val x = floor(i * (bw + gapX))
                    val lit = slept?.let { (it / 30f).roundToInt().coerceIn(0, levels) } ?: 0
                    val plan = planned?.let { (it / 30f).roundToInt().coerceIn(1, levels) }
                    val c = when {
                        slept == null -> Ink.raised
                        slept >= 7 * 60 -> Ink.sky
                        slept >= 6 * 60 -> Ink.sky.copy(alpha = 0.65f)
                        else -> Ink.bad
                    }
                    for (l in 0 until levels) {
                        val y = size.height - (l + 1) * bh - l * gapY
                        val col = when {
                            plan != null && l == plan - 1 && l >= lit -> Ink.amber
                            l < lit -> c
                            l % 2 == 1 && (l + 1) % 4 == 0 -> Ink.raised // faint 2-hour rungs
                            else -> Color.Transparent
                        }
                        if (col != Color.Transparent) drawRect(col, Offset(x, y), Size(bw, bh))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                nights.forEach { (d, _, _) ->
                    Text(
                        d.dayOfWeek.name.take(1),
                        style = Type.label.copy(color = if (d == Clock.today()) Ink.amber else Ink.faint, letterSpacing = 0.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 14-day completion columns
        Panel(Modifier.appear(5), tab = "Two weeks", sprite = Sprites.tabInsights) {
            Text("Column = share of tasks finished, colour = efficiency", style = Type.small)
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(116.dp)) {
                val n = days.size
                val levels = 10
                val gapX = 5.dp.toPx()
                val gapY = 2.dp.toPx()
                val bw = floor((size.width - gapX * (n - 1)) / n)
                val bh = floor((size.height - 10.dp.toPx() - gapY * (levels - 1)) / levels)
                days.forEachIndexed { i, d ->
                    val x = floor(i * (bw + gapX))
                    val filled = if (d.planned == 0) 0 else (d.completion * levels).roundToInt()
                    val c = d.avgEff?.let { effColor(it) } ?: Ink.dim
                    for (l in 0 until levels) {
                        val y = size.height - 10.dp.toPx() - (l + 1) * bh - l * gapY
                        val col = when {
                            d.planned == 0 -> if (l == 0) Ink.raised else Color.Transparent
                            l < filled -> c
                            else -> Ink.raised
                        }
                        drawRect(col, Offset(x, y), Size(bw, bh))
                    }
                    if (d.locked) drawRect(Ink.amber, Offset(x + bw / 2 - 2.dp.toPx(), size.height - 6.dp.toPx()), Size(4.dp.toPx(), 4.dp.toPx()))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    Text(
                        d.date.dayOfWeek.name.take(1),
                        style = Type.label.copy(color = if (d.date == Clock.today()) Ink.amber else Ink.faint, letterSpacing = 0.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Block(Ink.amber, 6.dp)
                Spacer(Modifier.width(8.dp))
                Text("plan locked in before 3 AM", style = Type.small)
            }
        }

        // Efficiency by hour
        Panel(Modifier.appear(6), tab = "Best hours", tabColor = Ink.good, sprite = Sprites.tabToday) {
            Text("Average efficiency by start hour, last 4 weeks", style = Type.small)
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val n = hours.size
                val gap = 3.dp.toPx()
                val w = floor((size.width - gap * (n - 1)) / n)
                val unit = floor(size.height / 4)
                hours.forEachIndexed { i, (_, e) ->
                    val x = floor(i * (w + gap))
                    // Four stacked blocks per hour; more lit blocks = higher efficiency.
                    val lit = if (e == null) 0 else ((e / 100f) * 4).roundToInt().coerceIn(1, 4)
                    for (b in 0 until 4) {
                        val y = size.height - (b + 1) * unit
                        val col = if (e != null && b < lit) effColor(e) else Ink.raised
                        drawRect(col, Offset(x, y + 1), Size(w, unit - 2))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("5a", "9a", "1p", "5p", "9p", "12").forEach { Text(it, style = Type.label) }
            }
            hours.filter { it.second != null }.maxByOrNull { it.second!! }?.let { (h, e) ->
                Spacer(Modifier.height(10.dp))
                Text("Peak: blocks starting at ${Messages.time(h * 60)} average $e%.", style = Type.small.copy(color = Ink.text))
            }
        }

        if (cats.isNotEmpty()) {
            Panel(Modifier.appear(7), tab = "Where the week went", tabColor = Ink.sky, sprite = Sprites.catStudy) {
                Text("Planned (faint) against finished, by type", style = Type.small)
                Spacer(Modifier.height(12.dp))
                val max = cats.maxOf { it.plannedMin }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cats.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelSprite(c.cat.sprite(), Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(c.cat.label, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(60.dp))
                            PixelMeter(
                                c.doneMin / max.toFloat(), Modifier.weight(1f), c.cat.tint(), blocks = 16, height = 10.dp,
                                ghost = c.plannedMin / max.toFloat(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${Messages.duration(c.doneMin)}/${Messages.duration(c.plannedMin)}",
                                style = Type.num.copy(fontSize = 12.sp, color = Ink.dim),
                                modifier = Modifier.width(72.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }

        if (apps.isNotEmpty()) {
            Panel(Modifier.appear(8), tab = "What pulled you away", tabColor = Ink.bad, sprite = Sprites.badgeGhost) {
                Text("Minutes on distracting apps during tasks this week", style = Type.small)
                Spacer(Modifier.height(12.dp))
                val max = apps.maxOf { it.second }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    apps.forEach { (app, min) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(92.dp), maxLines = 1)
                            PixelMeter(min / max.toFloat(), Modifier.weight(1f), Ink.bad, blocks = 16, height = 10.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(Messages.duration(min), style = Type.num.copy(fontSize = 12.sp, color = Ink.dim), modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        if (facts.isNotEmpty()) {
            Label("Patterns")
            facts.forEachIndexed { i, f ->
                SpeechBubble("", Modifier.appear(9 + i), fromRight = i % 2 == 1, speaker = Sprites.climberA) {
                    Text(f.head, style = Type.title)
                    Spacer(Modifier.height(4.dp))
                    Text(f.body, style = Type.small)
                }
            }
        } else if (hasData) {
            Panel {
                Text("Patterns need a few more days", style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text("After about a week of check-outs this section points out your best hours, your biggest time drains and where plans slip.", style = Type.small)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Tile(sprite: Sprite, title: String, value: String, sub: String, modifier: Modifier, color: Color?) {
    PixelBox(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(sprite, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Label(title)
            }
            Spacer(Modifier.height(8.dp))
            CountText(value, Type.h1.copy(color = color ?: Ink.text))
            Text(sub, style = Type.small, maxLines = 1)
        }
    }
}
