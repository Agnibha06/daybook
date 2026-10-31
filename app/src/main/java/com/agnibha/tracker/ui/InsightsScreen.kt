package com.agnibha.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Insights
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store

@Composable
fun InsightsScreen() {
    val s = Store.state
    val sum = Insights.summary(s, 7)
    val days = Insights.daily(s, 14)
    val hours = Insights.byHour(s, 28)
    val cats = Insights.categories(s, 7)
    val apps = Insights.apps(s, 7)
    val facts = Insights.facts(s)
    val hasData = sum.checked > 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("Last 7 days")
        Text("Insights", style = Type.h1)

        if (!hasData) {
            Panel {
                Text("Your numbers show up here", style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check out a few tasks and this page fills with your completion rate, efficiency by hour, the apps that cost you the most focus, and patterns in how you work.",
                    style = Type.small,
                )
            }
        }

        // Headline numbers
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Completion", sum.completion?.let { "$it%" } ?: "–", "of tasks that were due", Modifier.weight(1f), sum.completion?.let { effColor(it) })
            Tile("Efficiency", sum.avgEff?.let { "$it%" } ?: "–", "average per task", Modifier.weight(1f), sum.avgEff?.let { effColor(it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Focus time", if (sum.focusMin > 0) Messages.duration(sum.focusMin) else "–", "study, class, lab, project", Modifier.weight(1f), null)
            Tile("Per unlock", sum.minPerUnlock?.let { "${it}m" } ?: "–", "focus minutes between unlocks", Modifier.weight(1f), null)
        }

        // 14-day completion bars
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Two weeks", style = Type.title, modifier = Modifier.weight(1f))
                Text("bar = tasks finished, colour = efficiency", style = Type.small)
            }
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val n = days.size
                val gap = 6.dp.toPx()
                val bw = (size.width - gap * (n - 1)) / n
                // Guides at 50% and 100%
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = size.height * (1f - f)
                    drawLine(Ink.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                days.forEachIndexed { i, d ->
                    val x = i * (bw + gap)
                    val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    if (d.planned > 0) {
                        drawRoundRect(Ink.raised, Offset(x, 0f), Size(bw, size.height), r)
                        val h = size.height * d.completion.coerceIn(0f, 1f)
                        if (h > 0f) drawRoundRect(d.avgEff?.let { effColor(it) } ?: Ink.dim, Offset(x, size.height - h), Size(bw, h), r)
                    }
                    if (d.locked) drawCircle(Ink.amber, 2.5.dp.toPx(), Offset(x + bw / 2, size.height + 0f))
                }
            }
            Spacer(Modifier.height(8.dp))
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
            Text("Amber dot = plan locked in before 3 AM.", style = Type.small)
        }

        // Efficiency by hour
        Panel {
            Text("When you focus best", style = Type.title)
            Text("Average efficiency by start hour, last 4 weeks", style = Type.small)
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val n = hours.size
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                hours.forEachIndexed { i, (_, e) ->
                    val x = i * (w + gap)
                    val c = if (e == null) Ink.raised else effColor(e).copy(alpha = 0.35f + 0.65f * (e / 100f))
                    drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("5 AM", "9 AM", "1 PM", "5 PM", "9 PM", "12").forEach { Text(it, style = Type.small.copy(fontSize = Type.label.fontSize)) }
            }
            hours.filter { it.second != null }.maxByOrNull { it.second!! }?.let { (h, e) ->
                Spacer(Modifier.height(10.dp))
                Text("Peak: blocks starting at ${Messages.time(h * 60)} average $e%.", style = Type.small.copy(color = Ink.text))
            }
        }

        // Categories
        if (cats.isNotEmpty()) {
            Panel {
                Text("Where the week went", style = Type.title)
                Text("Planned vs finished, by type", style = Type.small)
                Spacer(Modifier.height(12.dp))
                val max = cats.maxOf { it.plannedMin }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cats.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.cat.label, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(66.dp))
                            Box(Modifier.weight(1f).height(10.dp)) {
                                Box(Modifier.fillMaxWidth(c.plannedMin / max.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.cat.tint().copy(alpha = 0.22f)))
                                Box(Modifier.fillMaxWidth(c.doneMin / max.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.cat.tint()))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${Messages.duration(c.doneMin)} / ${Messages.duration(c.plannedMin)}",
                                style = Type.num.copy(fontSize = Type.label.fontSize, color = Ink.dim),
                                modifier = Modifier.width(84.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }

        // Distracting apps
        if (apps.isNotEmpty()) {
            Panel {
                Text("What pulled you away", style = Type.title)
                Text("Minutes on distracting apps during tasks this week", style = Type.small)
                Spacer(Modifier.height(12.dp))
                val max = apps.maxOf { it.second }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    apps.forEach { (app, min) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app, style = Type.small.copy(color = Ink.text), modifier = Modifier.width(96.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.raised)) {
                                Box(Modifier.fillMaxWidth(min / max.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.bad))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(Messages.duration(min), style = Type.num.copy(fontSize = Type.label.fontSize, color = Ink.dim), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        // Patterns
        if (facts.isNotEmpty()) {
            Label("Patterns")
            facts.forEach { f ->
                Panel {
                    Text(f.head, style = Type.title.copy(color = Ink.text))
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
private fun Tile(title: String, value: String, sub: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color?) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Ink.surface).padding(14.dp)) {
        Label(title)
        Spacer(Modifier.height(8.dp))
        Text(value, style = Type.bigNum.copy(fontSize = Type.h1.fontSize, lineHeight = Type.h1.lineHeight, color = color ?: Ink.text))
        Text(sub, style = Type.small, maxLines = 2)
    }
}
