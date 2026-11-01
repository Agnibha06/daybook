package com.agnibha.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agnibha.tracker.Alarms
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Scoring
import com.agnibha.tracker.Store
import com.agnibha.tracker.Task
import java.time.format.DateTimeFormatter

private val TODAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val s = Store.state
    val nowMs = rememberNow(15_000L)
    val nowMin = Clock.minutesNow()
    val key = Clock.todayKey()
    val plan = s.plan(key)
    val tasks = plan.sortedTasks
    val streak = Store.streak(s)
    val dayLive = Store.isLive(key)
    var adding by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label(Clock.today().format(TODAY_FMT))
                Spacer(Modifier.height(6.dp))
                Text("${Messages.greeting(Clock.now().hour)},\n${s.username}", style = Type.h1)
            }
            Box(Modifier.background(Ink.surface, PxShapeSmall).clickable { Nav.settings = true }.padding(8.dp)) {
                PixelSprite(Sprites.gear, Modifier.size(24.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(Sprites.tabClimb, streak.toString(), "streak", Ink.amber, Modifier.weight(1f))
            StatPill(Sprites.badgeCoin, s.totalPoints.toString(), "lv ${Scoring.level(s.totalPoints)}", Ink.text, Modifier.weight(1f))
            StatPill(Sprites.badgeSteady, "${Store.movesLeft(key)}", "moves", Ink.sky, Modifier.weight(1f))
        }

        // Tonight's check-in
        if (Store.streakAtRisk(s)) {
            Panel(border = Ink.amber) {
                Label("Planning window open", color = Ink.amber)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (streak > 0) "Lock in today before 3:00 AM to make it day ${streak + 1}." else "Lock in today before 3:00 AM to start your streak.",
                    style = Type.title,
                )
                Spacer(Modifier.height(8.dp))
                val left = Clock.millisToDeadline()
                PixelMeter(left / (3 * 3_600_000f), blocks = 18, color = Ink.amber)
                Spacer(Modifier.height(4.dp))
                Text("${countdown(left)} left", style = Type.num.copy(color = Ink.dim))
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Plan today") { Nav.tab = Tab.PLAN }
            }
        } else if (!plan.isLocked && tasks.isEmpty()) {
            Panel {
                Text("No plan for today", style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Today wasn't locked in before 3:00 AM, so it doesn't count for the streak. You can still add tasks and they'll ring as usual. Tonight's window opens at 12:00 AM.",
                    style = Type.small,
                )
            }
        }

        // Now / next
        val current = tasks.firstOrNull { it.result == null && it.startMin <= nowMin && nowMin < it.endMin }
        val next = tasks.firstOrNull { it.result == null && it.startMin > nowMin }
        if (dayLive && current != null) {
            NowCard(current, nowMs) { sheetFor = current.id }
        } else if (dayLive && next != null) {
            Panel(modifier = Modifier.clickable { sheetFor = next.id }) {
                Label("Up next")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(next, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(next.title, style = Type.h2)
                        Text("${Messages.time(next.startMin)} – ${Messages.time(next.endMin)}", style = Type.small)
                    }
                    Text("in ${Messages.duration((next.startMin - nowMin).coerceAtLeast(1))}", style = Type.num.copy(color = Ink.amber))
                }
            }
        } else if (dayLive && tasks.isNotEmpty() && tasks.all { it.result != null }) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.badgeSweep, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Day closed", style = Type.h2)
                        Text("Every task is checked out. Plan tomorrow after midnight.", style = Type.small)
                    }
                }
            }
        }

        // Tasks that ended without a check-out
        val pending = if (dayLive) tasks.filter { it.result == null && it.endMin <= nowMin } else emptyList()
        AnimatedVisibility(pending.isNotEmpty(), enter = fadeIn() + expandVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Needs check-out", color = Ink.amber)
                pending.forEach { t -> CheckOutCard(t, key) }
            }
        }

        // Day progress
        if (tasks.isNotEmpty()) {
            val checked = tasks.mapNotNull { it.result }
            val done = checked.count { it.outcome == Outcome.DONE }
            val eff = checked.filter { it.outcome != Outcome.MISSED }.map { it.efficiency }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Label("Today")
                Text(
                    "$done of ${tasks.size} done" + (if (eff.isNotEmpty()) " · ${eff.average().toInt()}% avg" else ""),
                    style = Type.small,
                )
            }
            PixelMeter(checked.size / tasks.size.toFloat(), blocks = tasks.size.coerceIn(4, 24), color = Ink.amber)
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.forEach { t ->
                    val (status, color) = statusOf(t, nowMin, dayLive)
                    TaskRow(t, status, color, highlight = t.id == current?.id) { sheetFor = t.id }
                }
            }
        }

        GhostButton("+  Add a task to today", Modifier.fillMaxWidth(), color = Ink.amber) { adding = true }
        Spacer(Modifier.height(12.dp))
    }

    sheetFor?.let { id -> TaskSheet(key, id) { sheetFor = null } }

    if (adding) {
        TaskEditor(
            initial = null,
            others = tasks,
            timesLocked = false,
            onDismiss = { adding = false },
            onSave = { t ->
                Store.upsertTask(key, t)
                Alarms.scheduleTasks(ctx, key)
                adding = false
                Toaster.show("Added. It'll ring 10 minutes before.")
            },
            onDelete = null,
        )
    }
}

fun statusOf(t: Task, nowMin: Int, dayLive: Boolean): Pair<String, Color> {
    val r = t.result
    val isNow = r == null && t.startMin <= nowMin && nowMin < t.endMin
    return when {
        r != null && r.outcome == Outcome.MISSED -> "Missed" to Ink.bad
        r != null -> "${r.efficiency}%" to effColor(r.efficiency)
        !dayLive -> "Draft" to Ink.faint
        isNow -> "Now" to Ink.amber
        t.endMin <= nowMin -> "Check out" to Ink.amber
        else -> Messages.time(t.startMin) to Ink.faint
    }
}

@Composable
private fun StatPill(sprite: Sprite, value: String, label: String, color: Color, modifier: Modifier) {
    Row(
        modifier.background(Ink.surface, PxShapeSmall).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite(sprite, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = Type.num.copy(color = color))
            Text(label, style = Type.label.copy(fontSize = Type.label.fontSize * 0.9f))
        }
    }
}

@Composable
private fun NowCard(t: Task, nowMs: Long, onOpen: () -> Unit) {
    val start = Clock.epoch(Clock.todayKey(), t.startMin)
    val end = Clock.epoch(Clock.todayKey(), t.endMin)
    val progress = ((nowMs - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
    Panel(modifier = Modifier.clickable(onClick = onOpen), border = t.category.tint().copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(t.category.sprite(), Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Label("Now · ${t.category.label}", color = t.category.tint())
                }
                Spacer(Modifier.height(8.dp))
                Text(t.title, style = Type.h2)
                Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)}", style = Type.small)
            }
            Spacer(Modifier.width(12.dp))
            PixelDial(progress, 84.dp, t.category.tint(), blocks = 24) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdown(end - nowMs), style = Type.num)
                    Text("left", style = Type.small.copy(fontSize = Type.label.fontSize))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton("Focus") { Nav.focus = Clock.todayKey() to t.id } }
            GhostButton("More", Modifier.weight(1f).height(52.dp), onClick = onOpen)
        }
    }
}

@Composable
private fun CheckOutCard(t: Task, key: String) {
    val ctx = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(t, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = Type.title)
                Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)}", style = Type.small)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Done", false, Ink.good) { Store.checkOut(ctx, key, t.id, Outcome.DONE, false)?.let { Nav.showResult(key, t.id, it) } }
            Chip("Partly", false, Ink.amber) { Store.checkOut(ctx, key, t.id, Outcome.PARTIAL, false)?.let { Nav.showResult(key, t.id, it) } }
            Chip("Missed", false, Ink.bad) { Store.checkOut(ctx, key, t.id, Outcome.MISSED, false)?.let { Nav.showResult(key, t.id, it) } }
        }
    }
}
