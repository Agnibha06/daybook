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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agnibha.tracker.Category
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Store
import com.agnibha.tracker.Task
import kotlinx.coroutines.delay

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
fun TimePickDialog(initialMin: Int, title: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) =
    PixelTimeDialog(initialMin, title, onDismiss, onPick)

/**
 * Add or edit one task. When [timesLocked] the day is live: times change only through a move,
 * and the task can't be deleted.
 */
@Composable
fun TaskEditor(
    initial: Task?,
    others: List<Task>,
    timesLocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val suggestedStart = remember {
        initial?.startMin ?: (others.maxOfOrNull { it.endMin } ?: (9 * 60)).coerceAtMost(23 * 60)
    }
    val lockTimes = timesLocked && initial != null
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: Category.STUDY) }
    var start by remember { mutableIntStateOf(suggestedStart) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: (suggestedStart + 60).coerceAtMost(Clock.DAY_END_MIN)) }
    var picking by remember { mutableStateOf<String?>(null) }

    val valid = title.isNotBlank() && end > start
    val clash = others.firstOrNull { it.startMin < end && start < it.endMin }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .background(Ink.surface, PxShape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(cat.sprite(), Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (initial == null) "New task" else "Edit task", style = Type.h2)
            }
            PixelTextField(title, { title = it.take(60) }, "What are you doing?")
            Label("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c -> Chip(c.label, c == cat, c.tint(), c.sprite()) { cat = c } }
            }
            Label("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeBox("Starts", start, Modifier.weight(1f), enabled = !lockTimes) { picking = "start" }
                TimeBox("Ends", end, Modifier.weight(1f), enabled = !lockTimes) { picking = "end" }
            }
            if (lockTimes) {
                Text("The day is live, so times change only with a move. Use Move on the task.", style = Type.small)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { d ->
                        Chip(Messages.duration(d), end - start == d) { end = (start + d).coerceAtMost(Clock.DAY_END_MIN) }
                    }
                }
                if (end <= start) Text("End time has to be after the start.", style = Type.small.copy(color = Ink.bad))
                else if (clash != null) Text("Overlaps with \"${clash.title}\".", style = Type.small.copy(color = Ink.amber))
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save", enabled = valid) {
                onSave(
                    (initial ?: Task(Store.newTaskId(), "", cat, start, end)).copy(
                        title = title.trim(),
                        category = cat,
                        startMin = start,
                        endMin = end,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cancel", style = Type.num.copy(color = Ink.dim), modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                if (onDelete != null) {
                    Text("Delete", style = Type.num.copy(color = Ink.bad), modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
                }
            }
        }
    }

    picking?.let { which ->
        TimePickDialog(if (which == "start") start else end, if (which == "start") "Starts at" else "Ends at", { picking = null }) { m ->
            if (which == "start") {
                val len = (end - start).coerceAtLeast(30)
                start = m
                end = (m + len).coerceAtMost(Clock.DAY_END_MIN)
            } else {
                end = m
            }
            picking = null
        }
    }
}

@Composable
private fun TimeBox(label: String, min: Int, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .background(Ink.bg, PxShapeSmall)
            .border(2.dp, if (enabled) Ink.line else Ink.surface, PxShapeSmall)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = Type.small)
        Text(Messages.time(min), style = Type.num.copy(fontSize = Type.h2.fontSize, color = if (enabled) Ink.text else Ink.dim))
    }
}

/** Everything you can do to one task, from a tap on it. */
@Composable
fun TaskSheet(dateKey: String, taskId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val task = Store.state.plan(dateKey).tasks.firstOrNull { it.id == taskId }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = rememberNow(15_000L)
    if (task == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val live = Store.isLive(dateKey)
    val isToday = dateKey == Clock.todayKey()
    val nowMin = Clock.minutesNow()
    val running = isToday && task.result == null && task.startMin <= nowMin && nowMin < task.endMin
    val started = !isToday || task.startMin <= nowMin
    val movesLeft = Store.movesLeft(dateKey)

    fun move(r: Store.MoveResult, what: String) {
        Toaster.show(Messages.moveOutcome(r, what))
        if (r == Store.MoveResult.Ok) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Ink.surface,
        shape = PxShape,
        dragHandle = {
            Row(Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) { Block(Ink.line, 5.dp) }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(task, 44.dp, done = task.result != null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = Type.h2)
                    Text(
                        "${Messages.time(task.startMin)} – ${Messages.time(task.endMin)} · ${task.category.label}" +
                            if (task.moved > 0) " · moved ${task.moved}×" else "",
                        style = Type.small,
                    )
                }
            }

            val r = task.result
            if (r != null) {
                Panel(color = Ink.raised) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (r.outcome == Outcome.MISSED) "Missed" else "${r.efficiency}%",
                            style = Type.h1.copy(color = if (r.outcome == Outcome.MISSED) Ink.faint else effColor(r.efficiency)),
                            modifier = Modifier.weight(1f),
                        )
                        Text("+${r.points} pts", style = Type.num.copy(color = Ink.amber))
                    }
                    if (r.unlocks != null && r.outcome != Outcome.MISSED) {
                        Text("${r.unlocks} unlocks · ${r.distractMin ?: 0}m on distracting apps", style = Type.small)
                    }
                }
                GhostButton("See full result", Modifier.fillMaxWidth()) {
                    Nav.result = task to r
                    onDismiss()
                }
                return@Column
            }

            if (live && isToday) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("Focus mode") {
                            Nav.focus = dateKey to task.id
                            onDismiss()
                        }
                    }
                    if (running) {
                        GhostButton("Done early", Modifier.weight(1f).height(52.dp)) {
                            Store.checkOut(ctx, dateKey, task.id, Outcome.DONE, early = true)?.let { Nav.showResult(dateKey, task.id, it) }
                            onDismiss()
                        }
                    }
                }
            }

            if (live && started && !running) {
                Label("Check out")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Done", false, Ink.good) {
                        Store.checkOut(ctx, dateKey, task.id, Outcome.DONE, false)?.let { Nav.showResult(dateKey, task.id, it) }
                        onDismiss()
                    }
                    Chip("Partly", false, Ink.amber) {
                        Store.checkOut(ctx, dateKey, task.id, Outcome.PARTIAL, false)?.let { Nav.showResult(dateKey, task.id, it) }
                        onDismiss()
                    }
                    Chip("Missed", false, Ink.bad) {
                        Store.checkOut(ctx, dateKey, task.id, Outcome.MISSED, false)?.let { Nav.showResult(dateKey, task.id, it) }
                        onDismiss()
                    }
                }
            }

            if (!started || running || !live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Move", Modifier.weight(1f))
                    if (live) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(com.agnibha.tracker.MAX_MOVES) { i -> Block(if (i < movesLeft) Ink.amber else Ink.line, 10.dp) }
                            Spacer(Modifier.width(6.dp))
                            Text(Messages.movesLeft(movesLeft), style = Type.small)
                        }
                    } else {
                        Text("Free until the day goes live", style = Type.small)
                    }
                }
                val can = movesLeft > 0
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        Chip("+${Messages.duration(m)}", false, if (can) Ink.sky else Ink.faint) {
                            move(Store.push(ctx, dateKey, task.id, m), "Moved to ${Store.findTask(dateKey, task.id)?.startMin?.let { Messages.time(it) } ?: "later"}")
                        }
                    }
                    if (running) {
                        Chip("Extend 15m", false, if (can) Ink.sky else Ink.faint) {
                            move(Store.extend(ctx, dateKey, task.id, 15), "Extended to ${Store.findTask(dateKey, task.id)?.endMin?.let { Messages.time(it) } ?: ""}")
                        }
                    }
                    Chip("Pick a time", false, if (can) Ink.sky else Ink.faint) { if (can) picking = true else move(Store.MoveResult.NoMovesLeft, "") }
                    Chip("Tomorrow", false, if (can) Ink.sky else Ink.faint) {
                        move(Store.postponeToTomorrow(ctx, dateKey, task.id), "Moved to tomorrow's plan")
                    }
                }
            }

            GhostButton(if (live) "Rename or change type" else "Edit", Modifier.fillMaxWidth()) { editing = true }
        }
    }

    if (editing) {
        TaskEditor(
            initial = task,
            others = Store.state.plan(dateKey).tasks.filter { it.id != task.id },
            timesLocked = live,
            onDismiss = { editing = false },
            onSave = { t ->
                Store.upsertTask(dateKey, t)
                com.agnibha.tracker.Alarms.scheduleTasks(ctx, dateKey)
                editing = false
                onDismiss()
            },
            onDelete = if (live) null else ({
                Store.deleteTask(ctx, dateKey, task.id)
                editing = false
                onDismiss()
            }),
        )
    }

    if (picking) {
        TimePickDialog(task.startMin, "New start time", { picking = false }) { m ->
            picking = false
            val end = (m + task.durationMin).coerceAtMost(Clock.DAY_END_MIN)
            move(Store.retime(ctx, dateKey, task.id, m, end), "Moved to ${Messages.time(m)}")
        }
    }
}

/** A list row for a task: pixel icon, title, time and a status on the right. */
@Composable
fun TaskRow(
    t: Task,
    status: String,
    statusColor: Color,
    highlight: Boolean = false,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val done = t.result != null
    PixelBox(
        modifier.fillMaxWidth(),
        color = if (highlight) Ink.raised else Ink.surface,
        border = if (highlight) t.category.tint().copy(alpha = 0.7f) else null,
        depth = if (done) 2.dp else 4.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(t, 38.dp, done)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = Type.title.copy(color = if (done) Ink.dim else Ink.text), maxLines = 1)
                Text(
                    subtitle ?: ("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)}" + if (t.moved > 0) " · moved" else ""),
                    style = Type.small,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (t.result != null && t.result.outcome == Outcome.DONE) {
                PixelSprite(Sprites.check, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            } else if (t.result?.outcome == Outcome.MISSED) {
                PixelSprite(Sprites.cross, Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(status, style = Type.num.copy(fontSize = Type.small.fontSize, color = statusColor))
        }
    }
}
