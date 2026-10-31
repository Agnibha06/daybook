package com.agnibha.tracker.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
fun TodayScreen(
    onGoToPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckOut: (Task, Outcome, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val s = Store.state
    val nowMs = rememberNow(15_000L)
    val nowMin = Clock.minutesNow()
    val key = Clock.todayKey()
    val plan = s.plan(key)
    val tasks = plan.sortedTasks
    val streak = Store.streak(s)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    val dayLive = plan.isLocked || !Clock.inWindow()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label(Clock.today().format(TODAY_FMT))
                Spacer(Modifier.height(4.dp))
                Text("${Messages.greeting(Clock.now().hour)}, ${s.username}", style = Type.h1)
            }
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenSettings).padding(6.dp)) {
                TabGlyph(Glyph.SETTINGS, Ink.dim, 22.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(streak.toString(), "day streak", Ink.amber, peak = true)
            StatPill(s.totalPoints.toString(), "points · level ${Scoring.level(s.totalPoints)}", Ink.text)
        }

        // Tonight's check-in
        if (Store.streakAtRisk(s)) {
            Panel {
                Label("Planning window open", color = Ink.amber)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (streak > 0) "Lock in today before 3:00 AM to make it day ${streak + 1}." else "Lock in today before 3:00 AM to start your streak.",
                    style = Type.title,
                )
                Text("${countdown(Clock.millisToDeadline())} left", style = Type.num.copy(color = Ink.dim))
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Plan today", onClick = onGoToPlan)
            }
        } else if (!plan.isLocked && tasks.isEmpty()) {
            Panel {
                Text("No plan for today", style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Today wasn't locked in before 3:00 AM, so it doesn't count for the streak. You can still add tasks and they will ring as usual. Tonight's window opens at 12:00 AM.",
                    style = Type.small,
                )
            }
        }

        // Now / next
        val current = tasks.firstOrNull { it.result == null && it.startMin <= nowMin && nowMin < it.endMin }
        val next = tasks.firstOrNull { it.result == null && it.startMin > nowMin }
        if (dayLive && current != null) {
            NowCard(current, nowMs) { onCheckOut(current, Outcome.DONE, true) }
        } else if (dayLive && next != null) {
            Panel {
                Label("Next")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(next.title, style = Type.h2)
                        Text("${Messages.time(next.startMin)} – ${Messages.time(next.endMin)} · ${next.category.label}", style = Type.small)
                    }
                    Text("in ${Messages.duration((next.startMin - nowMin).coerceAtLeast(1))}", style = Type.num.copy(color = Ink.amber))
                }
            }
        }

        // Tasks that ended without a check-out
        val pending = if (dayLive) tasks.filter { it.result == null && it.endMin <= nowMin } else emptyList()
        if (pending.isNotEmpty()) {
            Label("Needs check-out", color = Ink.amber)
            pending.forEach { t -> CheckOutRow(t) { o -> onCheckOut(t, o, false) } }
        }

        // Day progress
        if (tasks.isNotEmpty()) {
            val checked = tasks.mapNotNull { it.result }
            val done = checked.count { it.outcome == Outcome.DONE }
            val eff = checked.filter { it.outcome != Outcome.MISSED }.map { it.efficiency }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Label("Today")
                Text(
                    "$done of ${tasks.size} done" + (if (eff.isNotEmpty()) " · ${eff.average().toInt()}% avg" else ""),
                    style = Type.small,
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ink.raised)) {
                Box(
                    Modifier.fillMaxWidth(checked.size / tasks.size.toFloat()).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Ink.amber),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.forEach { t -> TimelineRow(t, nowMin, dayLive) { if (t.result == null) editing = t } }
            }
        }

        GhostButton("+  Add a task to today", Modifier.fillMaxWidth(), color = Ink.amber) { adding = true }
        Spacer(Modifier.height(12.dp))
    }

    if (adding || editing != null) {
        TaskEditor(
            initial = editing,
            others = tasks.filter { it.id != editing?.id },
            onDismiss = { adding = false; editing = null },
            onSave = { t ->
                Store.upsertTask(key, t)
                Alarms.scheduleTasks(ctx, key)
                adding = false; editing = null
            },
            onDelete = editing?.let { e ->
                {
                    Store.deleteTask(key, e.id)
                    Alarms.cancelTask(ctx, key, e.id)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun StatPill(value: String, label: String, color: Color, peak: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Ink.surface).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (peak) {
            PeakGlyph(14.dp, color)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = Type.num.copy(color = color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Type.small)
    }
}

@Composable
private fun NowCard(t: Task, nowMs: Long, onFinishEarly: () -> Unit) {
    val start = Clock.epoch(Clock.todayKey(), t.startMin)
    val end = Clock.epoch(Clock.todayKey(), t.endMin)
    val progress = ((nowMs - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("Now", color = t.category.tint())
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = Type.h2)
                Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)} · ${t.category.label}", style = Type.small)
            }
            Spacer(Modifier.width(12.dp))
            Ring(progress, 76.dp, 7.dp, t.category.tint()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdown(end - nowMs), style = Type.num.copy(fontSize = Type.small.fontSize))
                    Text("left", style = Type.small.copy(fontSize = Type.label.fontSize))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Finished early",
            style = Type.small.copy(color = Ink.dim, fontWeight = FontWeight.Medium),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinishEarly).padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun CheckOutRow(t: Task, onPick: (Outcome) -> Unit) {
    Panel {
        Text(t.title, style = Type.title)
        Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)} · ${t.category.label}", style = Type.small)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Done", false, Ink.good) { onPick(Outcome.DONE) }
            Chip("Partly", false, Ink.amber) { onPick(Outcome.PARTIAL) }
            Chip("Missed", false, Ink.bad) { onPick(Outcome.MISSED) }
        }
    }
}

@Composable
private fun TimelineRow(t: Task, nowMin: Int, dayLive: Boolean, onClick: () -> Unit) {
    val r = t.result
    val isNow = r == null && t.startMin <= nowMin && nowMin < t.endMin
    val (status, color) = when {
        r != null && r.outcome == Outcome.MISSED -> "Missed" to Ink.bad
        r != null -> "${r.efficiency}%" to effColor(r.efficiency)
        !dayLive -> "Draft" to Ink.faint
        isNow -> "Now" to Ink.amber
        t.endMin <= nowMin -> "Check out" to Ink.amber
        else -> "Upcoming" to Ink.faint
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isNow) Ink.raised else Ink.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Messages.time(t.startMin), style = Type.num.copy(fontSize = Type.small.fontSize, color = if (r != null) Ink.faint else Ink.text), modifier = Modifier.width(72.dp))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(t.category.tint().copy(alpha = if (r != null) 0.45f else 1f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = Type.title.copy(color = if (r != null) Ink.dim else Ink.text), maxLines = 1)
            if (r != null && r.unlocks != null && r.outcome != Outcome.MISSED) {
                Text("${r.unlocks} unlocks · ${r.distractMin ?: 0}m distracted", style = Type.small)
            } else {
                Text(Messages.duration(t.durationMin), style = Type.small)
            }
        }
        Text(status, style = Type.num.copy(fontSize = Type.small.fontSize, color = color))
    }
}
