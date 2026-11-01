package com.agnibha.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
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
        // Header: the climber says hello
        Row(Modifier.fillMaxWidth().appear(0), verticalAlignment = Alignment.CenterVertically) {
            Label(Clock.today().format(TODAY_FMT), Modifier.weight(1f))
            PixelIconButton(Sprites.gear) { Nav.settings = true }
        }
        SpeechBubble(
            "",
            Modifier.appear(1),
            speaker = Sprites.climberA,
        ) {
            Text("${Messages.greeting(Clock.now().hour)}, ${s.username}", style = Type.h2)
            Text(headline(streak, plan.isLocked, tasks.size), style = Type.small)
        }

        // Bento: one tall streak tile, two short ones beside it
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).appear(2), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelBox(Modifier.weight(1.15f).fillMaxHeight(), color = Ink.raised, onClick = { Nav.tab = Tab.CLIMB }) {
                Column {
                    PixelSprite(Sprites.tabClimb, Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    CountText("$streak", Type.bigNum.copy(color = Ink.amber))
                    Text(if (streak == 1) "day streak" else "day streak", style = Type.small)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTile(Sprites.badgeCoin, "${s.totalPoints}", "points · lv ${Scoring.level(s.totalPoints)}", Ink.text)
                MiniTile(Sprites.badgeSteady, "${Store.movesLeft(key)}", "moves left", Ink.sky)
            }
        }

        Box(Modifier.appear(3)) { SleepCard() }

        // Tonight's check-in
        if (Store.streakAtRisk(s)) {
            Panel(Modifier.appear(4), border = Ink.amber, tab = "Planning window open", sprite = Sprites.badgeLock) {
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
            Box(Modifier.appear(4)) { NowCard(current, nowMs) { sheetFor = current.id } }
        } else if (dayLive && next != null) {
            Panel(Modifier.appear(4), tab = "Up next", tabColor = next.category.tint(), onClick = { sheetFor = next.id }) {
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
            Panel(Modifier.appear(4), tab = "Done", tabColor = Ink.good) {
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
                pending.forEach { t -> CheckOutCard(t, key) }
            }
        }

        // Day progress
        if (tasks.isNotEmpty()) {
            val checked = tasks.mapNotNull { it.result }
            val done = checked.count { it.outcome == Outcome.DONE }
            val eff = checked.filter { it.outcome != Outcome.MISSED }.map { it.efficiency }
            PixelDivider(Sprites.tabPlan)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Label("Today's path")
                Text(
                    "$done of ${tasks.size} done" + (if (eff.isNotEmpty()) " · ${eff.average().toInt()}% avg" else ""),
                    style = Type.small,
                )
            }
            PixelMeter(checked.size / tasks.size.toFloat(), blocks = tasks.size.coerceIn(4, 24), color = Ink.amber)
            Column(Modifier.animateContentSize()) {
                tasks.forEachIndexed { i, t ->
                    val (status, color) = statusOf(t, nowMin, dayLive)
                    PathRow(t, i, i == tasks.lastIndex, nowMin, Modifier.appear(5 + i)) {
                        TaskRow(t, status, color, highlight = t.id == current?.id) { sheetFor = t.id }
                    }
                }
            }
        }

        GhostButton("Add a task to today", Modifier.fillMaxWidth(), color = Ink.amber, icon = Sprites.plus) { adding = true }
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

private fun headline(streak: Int, locked: Boolean, tasks: Int): String = when {
    !locked && Clock.inWindow() -> "The planning window is open. Let's set up today."
    tasks == 0 -> "Nothing planned yet. Add something below."
    streak >= 7 -> "Day $streak of the climb. $tasks things on the path today."
    else -> "$tasks things on the path today. One at a time."
}

/** A bento tile: sprite, number, caption. */
@Composable
private fun MiniTile(sprite: Sprite, value: String, label: String, color: Color) {
    PixelBox(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(sprite, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                CountText(value, Type.num.copy(fontSize = 20.sp, color = color))
                Text(label, style = Type.small, maxLines = 1)
            }
        }
    }
}

/**
 * One stop on the day's path: a rail with a node on the left, and the card stepping
 * in and out along it so the list reads as a winding trail rather than a stack.
 */
@Composable
private fun PathRow(t: Task, index: Int, last: Boolean, nowMin: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val offsets = listOf(0, 14, 24, 14)
    val shift = offsets[index % offsets.size]
    val node = when {
        t.result != null && t.result.outcome == Outcome.MISSED -> Ink.bad
        t.result != null -> Ink.good
        t.startMin <= nowMin && nowMin < t.endMin -> Ink.amber
        else -> Ink.line
    }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width((shift + 22).dp).fillMaxHeight()) {
            val p = 3.dp.toPx()
            val x = size.width - 14.dp.toPx()
            val cy = size.height / 2f - 2.dp.toPx()
            // Dotted rail down to the next stop
            var y = 0f
            while (y < size.height) {
                if (last && y > cy) break
                drawRect(Ink.line, Offset(x, y), Size(p, p))
                y += p * 2
            }
            // Node: a 3x3 block with a lighter centre
            drawRect(node, Offset(x - p, cy - p), Size(p * 3, p * 3))
            drawRect(Ink.bg, Offset(x, cy), Size(p, p), alpha = 0.35f)
            // Spur to the card
            drawRect(Ink.line, Offset(x + 2 * p, cy), Size(size.width - x - 2 * p, p))
        }
        Box(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
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
private fun NowCard(t: Task, nowMs: Long, onOpen: () -> Unit) {
    val start = Clock.epoch(Clock.todayKey(), t.startMin)
    val end = Clock.epoch(Clock.todayKey(), t.endMin)
    val progress = ((nowMs - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
    Panel(border = t.category.tint().copy(alpha = 0.7f), tab = "Now", tabColor = t.category.tint(), sprite = t.category.sprite(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(t.category.label, color = t.category.tint())
                Spacer(Modifier.height(4.dp))
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
    Panel(tab = "Check out", tabColor = Ink.amber) {
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

/** Bedtime button at night, last night's sleep in the morning. */
@Composable
private fun SleepCard() {
    val ctx = LocalContext.current
    val s = Store.state
    val hour = Clock.now().hour
    val today = s.plan(Clock.todayKey())
    if (s.sleepingSince != null) return

    val wakeKey = Store.nextWakeDate()
    val target = s.plan(wakeKey)
    val bedAt = Store.bedtimeEpoch(wakeKey)
    val nearBed = bedAt != null && bedAt - System.currentTimeMillis() < 90 * 60_000L
    val missedWake = today.wakeMin != null && today.wakeAt == null &&
        (Store.wakeEpoch(Clock.todayKey()) ?: Long.MAX_VALUE) < System.currentTimeMillis() && hour in 4..13

    when {
        hour >= 21 || hour < 4 || nearBed -> Panel(border = Ink.sky.copy(alpha = 0.6f), tab = "Night", tabColor = Ink.sky, sprite = Sprites.zzz) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(Sprites.bed, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        target.sleepMin?.let { "Bedtime ${Messages.time(it)}" } ?: "Going to bed?",
                        style = Type.h2,
                    )
                    Text(
                        target.wakeMin?.let { "Alarm at ${Messages.time(it)}" } ?: "No wake-up set. Add one in the Plan tab.",
                        style = Type.small,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Sleep now", color = Ink.sky, shadow = Ink.sky.copy(alpha = 0.45f)) { Store.startSleep(ctx) }
        }
        missedWake -> Panel(tab = "Morning", tabColor = Ink.amber) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(Sprites.sunRise, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wake-up not logged", style = Type.title)
                    Text("Your alarm was at ${Messages.time(today.wakeMin ?: 0)}.", style = Type.small)
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("I'm awake") {
                val m = Store.wake(ctx)
                Toaster.show(if (m != null) "Slept ${Messages.duration(m)}. ${Messages.sleepVerdict(m)}" else "Wake-up logged.")
            }
        }
        today.wakeAt != null && hour < 13 -> {
            val slept = today.sleptMin
            PixelBox(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(Sprites.sunRise, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (slept != null) "Slept ${Messages.duration(slept)}" else "Up and running", style = Type.title)
                    val woke = java.time.Instant.ofEpochMilli(today.wakeAt).atZone(Clock.zone)
                    Text("Woke ${Messages.time(woke.hour * 60 + woke.minute)} · ${Messages.morningLine(today.snoozes)}", style = Type.small)
                }
                if (slept != null) {
                    Text(Messages.duration(slept), style = Type.num.copy(color = if (slept >= 7 * 60) Ink.good else if (slept >= 6 * 60) Ink.amber else Ink.bad))
                }
            } }
        }
    }
}
