@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agnibha.tracker.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agnibha.tracker.Alarms
import com.agnibha.tracker.Category
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store
import com.agnibha.tracker.Task
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Ticks every [ms] so countdowns stay current. */
@Composable
fun rememberNow(ms: Long = 20_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ms) {
        while (true) {
            delay(ms)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun countdown(millis: Long): String {
    val m = (millis.coerceAtLeast(0L) / 60000L).toInt()
    return Messages.duration(m.coerceAtLeast(1))
}

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
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    var lockedPoints by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label(if (isToday) "Planning today" else "Drafting tomorrow")
        Text(date.format(DAY_FMT), style = Type.h1)

        // Window status
        val windowOpen = Clock.inWindow() && isToday
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (windowOpen) Ink.amber else Ink.faint, 8.dp)
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
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    windowOpen -> "Lock it in before 3:00 AM to keep the streak. You can still add tasks afterwards."
                    todayPlan.isLocked && todayPlan.lockedAt != null ->
                        "Today was locked in at ${Messages.time(Instant.ofEpochMilli(todayPlan.lockedAt).atZone(Clock.zone).let { it.hour * 60 + it.minute })}. Draft tomorrow now; it can be locked in from midnight."
                    else -> "Draft now. You can lock it in between 12:00 and 3:00 AM."
                },
                style = Type.small,
            )
        }

        lockedPoints?.let { pts ->
            Panel {
                Label("Locked in", color = Ink.amber)
                Spacer(Modifier.height(4.dp))
                Text("Day ${Store.streak()} on the map. +$pts points.", style = Type.title)
            }
        }

        // Summary
        if (plan.tasks.isNotEmpty()) {
            val total = plan.tasks.sumOf { it.durationMin }
            val focus = plan.tasks.filter { it.category.isFocus }.sumOf { it.durationMin }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("${plan.tasks.size}", "tasks")
                Metric(Messages.duration(total), "planned")
                Metric(Messages.duration(focus), "focus")
            }
        }

        // Task list
        if (plan.tasks.isEmpty()) {
            Panel {
                Text("Nothing planned yet", style = Type.title)
                Spacer(Modifier.height(4.dp))
                Text("Add each block of the day with a start and end time: classes, labs, study, gym, practice.", style = Type.small)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.sortedTasks.forEach { t -> PlanRow(t) { editing = t } }
            }
        }

        GhostButton("+  Add task", Modifier.fillMaxWidth(), color = Ink.amber) { adding = true }
        if (plan.tasks.isEmpty()) {
            Text(
                "Copy the last plan",
                style = Type.small.copy(color = Ink.dim, fontWeight = FontWeight.Medium),
                modifier = Modifier.clickable { Store.copyLastPlan(key) }.padding(vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        if (!plan.isLocked) {
            PrimaryButton(
                when {
                    !windowOpen -> "Lock in opens at 12:00 AM"
                    plan.tasks.isEmpty() -> "Add a task to lock in"
                    else -> "Lock in today's plan"
                },
                enabled = Store.canLock(),
            ) { lockedPoints = Store.lockPlan(ctx) }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (adding || editing != null) {
        TaskEditor(
            initial = editing,
            others = plan.tasks.filter { it.id != editing?.id },
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
fun PlanRow(t: Task, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(78.dp)) {
            Text(Messages.time(t.startMin), style = Type.num.copy(fontSize = Type.small.fontSize))
            Text(Messages.time(t.endMin), style = Type.num.copy(fontSize = Type.small.fontSize, color = Ink.faint))
        }
        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(t.category.tint()))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = Type.title, maxLines = 2)
            Text("${t.category.label} · ${Messages.duration(t.durationMin)}", style = Type.small)
        }
    }
}

/** Add or edit one task. [onDelete] is null when adding. */
@Composable
fun TaskEditor(
    initial: Task?,
    others: List<Task>,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val suggestedStart = remember {
        initial?.startMin ?: (others.maxOfOrNull { it.endMin } ?: (9 * 60)).coerceAtMost(23 * 60)
    }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: Category.STUDY) }
    var start by remember { mutableIntStateOf(suggestedStart) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: (suggestedStart + 60).coerceAtMost(23 * 60 + 59)) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" or "end"

    val valid = title.isNotBlank() && end > start
    val clash = others.firstOrNull { it.startMin < end && start < it.endMin }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "New task" else "Edit task", style = Type.h2)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                singleLine = true,
                textStyle = Type.title,
                placeholder = { Text("What are you doing?", style = Type.title.copy(color = Ink.faint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.amber,
                    unfocusedBorderColor = Ink.line,
                    cursorColor = Ink.amber,
                    focusedContainerColor = Ink.bg,
                    unfocusedContainerColor = Ink.bg,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Label("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c -> Chip(c.label, c == cat, c.tint()) { cat = c } }
            }
            Label("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeBox("Starts", start, Modifier.weight(1f)) { picking = "start" }
                TimeBox("Ends", end, Modifier.weight(1f)) { picking = "end" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120).forEach { d ->
                    Chip(Messages.duration(d), end - start == d) { end = (start + d).coerceAtMost(23 * 60 + 59) }
                }
            }
            if (end <= start) Text("End time has to be after the start.", style = Type.small.copy(color = Ink.bad))
            else if (clash != null) Text("Overlaps with \"${clash.title}\".", style = Type.small.copy(color = Ink.amber))

            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save", enabled = valid) {
                onSave(
                    Task(
                        id = initial?.id ?: Store.newTaskId(),
                        title = title.trim(),
                        category = cat,
                        startMin = start,
                        endMin = end,
                        result = initial?.result,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cancel", style = Type.title.copy(color = Ink.dim), modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                if (onDelete != null) {
                    Text("Delete", style = Type.title.copy(color = Ink.bad), modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
                }
            }
        }
    }

    picking?.let { which ->
        val initialMin = if (which == "start") start else end
        val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = null },
            containerColor = Ink.surface,
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (which == "start") {
                        val len = (end - start).coerceAtLeast(30)
                        start = m
                        end = (m + len).coerceAtMost(23 * 60 + 59)
                    } else {
                        end = m
                    }
                    picking = null
                }) { Text("Set", style = Type.title.copy(color = Ink.amber)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("Cancel", style = Type.title.copy(color = Ink.dim)) }
            },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Ink.raised,
                        selectorColor = Ink.amber,
                        containerColor = Ink.surface,
                        periodSelectorBorderColor = Ink.line,
                        periodSelectorSelectedContainerColor = Ink.amberSoft,
                        periodSelectorSelectedContentColor = Ink.amber,
                        periodSelectorUnselectedContentColor = Ink.dim,
                        timeSelectorSelectedContainerColor = Ink.amberSoft,
                        timeSelectorSelectedContentColor = Ink.amber,
                        timeSelectorUnselectedContainerColor = Ink.raised,
                        timeSelectorUnselectedContentColor = Ink.text,
                        clockDialSelectedContentColor = Ink.bg,
                        clockDialUnselectedContentColor = Ink.text,
                    ),
                )
            },
        )
    }
}

@Composable
private fun TimeBox(label: String, min: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.bg)
            .border(1.dp, Ink.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = Type.small)
        Text(Messages.time(min), style = Type.num.copy(fontSize = Type.h2.fontSize))
    }
}
