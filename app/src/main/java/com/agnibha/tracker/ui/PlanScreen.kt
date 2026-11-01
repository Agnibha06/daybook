@file:OptIn(ExperimentalLayoutApi::class)

package com.agnibha.tracker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.agnibha.tracker.Alarms
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

val moodSprites: List<Sprite> get() = listOf(Sprites.mood1, Sprites.mood2, Sprites.mood3, Sprites.mood4, Sprites.mood5)

@Composable
fun PlanScreen() {
    val ctx = LocalContext.current
    val s = Store.state
    @Suppress("UNUSED_VARIABLE") val now = rememberNow()
    val date = Store.draftDate(s)
    val key = Clock.key(date)
    val plan = s.plan(key)
    val todayPlan = s.plan(Clock.todayKey())
    val isToday = date == Clock.today()
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val routine = Store.routineFor(key)
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label(if (isToday) "Planning today" else "Drafting tomorrow")
        Text(date.format(DAY_FMT), style = Type.h1)

        // Window status
        val windowOpen = Clock.inWindow() && isToday
        Panel(border = if (windowOpen) Ink.amber else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(if (windowOpen) Sprites.badgeLock else Sprites.badgeEarly, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (windowOpen) "Window open" else "Window opens at 12:00 AM",
                    style = Type.title.copy(color = if (windowOpen) Ink.amber else Ink.text),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (windowOpen) "${countdown(Clock.millisToDeadline())} left" else "in ${countdown(Clock.millisToWindow())}",
                    style = Type.num.copy(color = Ink.dim),
                )
            }
            if (windowOpen) {
                Spacer(Modifier.height(10.dp))
                PixelMeter(Clock.millisToDeadline() / (3 * 3_600_000f), blocks = 18)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    windowOpen -> "Lock it in before 3:00 AM to keep the streak. After that, each day gets two moves."
                    todayPlan.lockedAt != null ->
                        "Today was locked in at ${Messages.time(Instant.ofEpochMilli(todayPlan.lockedAt).atZone(Clock.zone).let { it.hour * 60 + it.minute })}. Draft tomorrow now; lock it in after midnight."
                    else -> "Draft now. You can lock it in between 12:00 and 3:00 AM."
                },
                style = Type.small,
            )
        }

        // Quick fills
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (routine.isNotEmpty()) {
                Chip("Load $weekday routine · ${routine.size}", false, Ink.sky, Sprites.tabPlan) {
                    val n = Store.loadRoutine(key)
                    Toaster.show(if (n == 0) "Routine is already in the plan." else "Added $n task${if (n == 1) "" else "s"} from your $weekday routine.")
                }
            }
            Chip("Copy last plan", false, Ink.dim) {
                val n = Store.copyLastPlan(key)
                Toaster.show(if (n == 0) "Nothing new to copy." else "Copied $n task${if (n == 1) "" else "s"}.")
            }
            if (plan.tasks.isNotEmpty()) {
                Chip("Save as $weekday routine", false, Ink.dim) {
                    Store.saveRoutine(key)
                    Toaster.show("Saved. Every $weekday can start from this plan.")
                }
            }
        }

        // Summary
        if (plan.tasks.isNotEmpty()) {
            val total = plan.tasks.sumOf { it.durationMin }
            val focus = plan.tasks.filter { it.category.isFocus }.sumOf { it.durationMin }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("${plan.tasks.size}", "tasks")
                Metric(Messages.duration(total), "planned")
                Metric(Messages.duration(focus), "focus", color = Ink.sky)
            }
            DayStrip(plan.sortedTasks)
        }

        // Task list
        if (plan.tasks.isEmpty()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.tabPlan, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Nothing planned yet", style = Type.title)
                        Text("Add each block with a start and end time: classes, labs, study, gym, practice.", style = Type.small)
                    }
                }
            }
        } else {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.sortedTasks.forEach { t ->
                    TaskRow(
                        t,
                        Messages.duration(t.durationMin),
                        Ink.faint,
                        subtitle = "${Messages.time(t.startMin)} – ${Messages.time(t.endMin)}" + (if (t.carriedFrom != null) " · carried over" else ""),
                    ) { editing = t.id }
                }
            }
        }

        GhostButton("+  Add task", Modifier.fillMaxWidth(), color = Ink.amber) { adding = true }

        // Reflection on yesterday, while the window is open.
        if (windowOpen && !plan.isLocked) {
            val yKey = Clock.key(date.minusDays(1))
            Reflection(yKey)
        }

        Spacer(Modifier.height(4.dp))
        if (!plan.isLocked) {
            PrimaryButton(
                when {
                    !windowOpen -> "Lock-in opens at 12:00 AM"
                    plan.tasks.isEmpty() -> "Add a task to lock in"
                    else -> "Lock in today"
                },
                enabled = Store.canLock(),
            ) {
                Store.lockPlan(ctx)?.let { Nav.locked = it }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { id ->
        val t = plan.tasks.firstOrNull { it.id == id }
        if (t != null) {
            TaskEditor(
                initial = t,
                others = plan.tasks.filter { it.id != id },
                timesLocked = Store.isLive(key),
                onDismiss = { editing = null },
                onSave = { nt ->
                    Store.upsertTask(key, nt)
                    Alarms.scheduleTasks(ctx, key)
                    editing = null
                },
                onDelete = if (Store.isLive(key)) null else ({
                    Store.deleteTask(ctx, key, id)
                    editing = null
                }),
            )
        } else editing = null
    }

    if (adding) {
        TaskEditor(
            initial = null,
            others = plan.tasks,
            timesLocked = false,
            onDismiss = { adding = false },
            onSave = { t ->
                Store.upsertTask(key, t)
                Alarms.scheduleTasks(ctx, key)
                adding = false
            },
            onDelete = null,
        )
    }
}

/** The day from 6 AM to midnight as a strip of blocks coloured by task. */
@Composable
private fun DayStrip(tasks: List<com.agnibha.tracker.Task>) {
    val startHour = minOf(6, (tasks.minOfOrNull { it.startMin } ?: 360) / 60)
    val slots = (24 - startHour) * 2 // half-hour blocks
    Column {
        Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (i in 0 until slots) {
                val m = startHour * 60 + i * 30
                val t = tasks.firstOrNull { it.startMin < m + 30 && m < it.endMin }
                Box(Modifier.weight(1f).height(16.dp).background(t?.category?.tint() ?: Ink.raised))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(startHour, (startHour + 24) / 2, 24).forEach { h ->
                Text(Messages.time((h % 24) * 60).replace(":00", ""), style = Type.label)
            }
        }
    }
}

@Composable
private fun Reflection(dateKey: String) {
    val p = Store.state.plan(dateKey)
    var note by remember(dateKey) { mutableStateOf(p.note) }
    var mood by remember(dateKey) { mutableIntStateOf(p.mood) }
    Panel {
        Label("Yesterday in one line")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            moodSprites.forEachIndexed { i, sp ->
                val v = i + 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .background(if (mood == v) Ink.raised else Color.Transparent, PxShapeSmall)
                            .border(2.dp, if (mood == v) Ink.amber else Color.Transparent, PxShapeSmall)
                            .clickable {
                                mood = v
                                Store.setReflection(dateKey, note, v)
                            }
                            .padding(6.dp),
                    ) {
                        PixelSprite(sp, Modifier.size(32.dp), muted = mood != 0 && mood != v)
                    }
                    Text(Messages.moodLabels[i], style = Type.label.copy(color = if (mood == v) Ink.amber else Ink.faint))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it.take(140)
                Store.setReflection(dateKey, note, mood)
            },
            textStyle = Type.body.copy(color = Ink.text),
            shape = PxShapeSmall,
            placeholder = { Text("What went well, what didn't", style = Type.body.copy(color = Ink.faint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = pixelFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text("Optional. It shows up in the Log and in your patterns.", style = Type.small)
    }
}
