package com.agnibha.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Clock
import com.agnibha.tracker.DayPlan
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Outcome
import com.agnibha.tracker.Store
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val LONG_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private fun completion(p: DayPlan): Float? {
    if (p.tasks.isEmpty()) return null
    val due = p.tasks.filter { it.result != null }
    if (due.isEmpty()) return null
    return p.tasks.sumOf {
        when (it.result?.outcome) { Outcome.DONE -> 1.0; Outcome.PARTIAL -> 0.5; else -> 0.0 }
    }.toFloat() / p.tasks.size
}

private fun cellColor(p: DayPlan, d: LocalDate): Color {
    val c = completion(p)
    return when {
        d.isAfter(Clock.today()) -> Color.Transparent
        c == null && p.isLocked -> Ink.amber.copy(alpha = 0.25f)
        c == null -> Ink.surface
        c >= 0.8f -> Ink.good
        c >= 0.5f -> Ink.good.copy(alpha = 0.55f)
        c >= 0.25f -> Ink.amber.copy(alpha = 0.7f)
        else -> Ink.bad.copy(alpha = 0.7f)
    }
}

@Composable
fun LogScreen() {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val s = Store.state

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("History")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.atDay(1).format(MONTH_FMT), style = Type.h1, modifier = Modifier.weight(1f))
            PixelIconButton(Sprites.arrowLeft) { month = month.minusMonths(1) }
            Spacer(Modifier.width(8.dp))
            PixelIconButton(Sprites.arrowRight, enabled = month < YearMonth.now()) { month = month.plusMonths(1) }
        }

        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { w -> dir * w / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(200)) { w -> -dir * w / 3 } + fadeOut(tween(150)))
            },
            label = "month",
        ) { m ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Panel {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(it, style = Type.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val first = m.atDay(1)
                    val lead = first.dayOfWeek.value - 1
                    val cells = lead + m.lengthOfMonth()
                    val rows = (cells + 6) / 7
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (r in 0 until rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (c in 0 until 7) {
                                    val idx = r * 7 + c - lead
                                    if (idx < 0 || idx >= m.lengthOfMonth()) {
                                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val d = m.atDay(idx + 1)
                                        DayCell(d, s.plan(Clock.key(d)), Modifier.weight(1f).pop(idx))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(Ink.good, "80%+")
                        Legend(Ink.amber.copy(alpha = 0.7f), "25–50%")
                        Legend(Ink.bad.copy(alpha = 0.7f), "low")
                        Legend(Ink.surface, "no plan", border = true)
                    }
                }
                MonthSummary(m)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Legend(color: Color, label: String, border: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color).then(if (border) Modifier.border(1.dp, Ink.line) else Modifier))
        Spacer(Modifier.width(5.dp))
        Text(label, style = Type.small.copy(fontSize = 11.sp))
    }
}

/** Calendar cells pop in one after another, like tiles being laid. */
@Composable
private fun Modifier.pop(index: Int): Modifier {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(12L * index)
        a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    return this.graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }
}

@Composable
private fun DayCell(d: LocalDate, p: DayPlan, modifier: Modifier) {
    val today = d == Clock.today()
    val future = d.isAfter(Clock.today())
    val fill = cellColor(p, d)
    val canOpen = !future && (p.tasks.isNotEmpty() || p.note.isNotBlank())
    Box(
        modifier
            .aspectRatio(1f)
            .background(fill, PxShapeSmall)
            .border(2.dp, if (today) Ink.amber else if (future) Ink.surface else Color.Transparent, PxShapeSmall)
            .clickable(enabled = canOpen) { Nav.day = Clock.key(d) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${d.dayOfMonth}",
            style = Type.num.copy(fontSize = 13.sp, color = if (fill == Ink.good) Ink.bg else if (future) Ink.faint else Ink.text),
        )
        if (p.isLocked) {
            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(5.dp).background(Ink.amber))
        }
    }
}

@Composable
private fun MonthSummary(m: YearMonth) {
    val s = Store.state
    val ps = (1..m.lengthOfMonth()).map { m.atDay(it) }.filter { !it.isAfter(Clock.today()) }.map { s.plan(Clock.key(it)) }
    val locked = ps.count { it.isLocked }
    val comps = ps.mapNotNull { completion(it) }
    val focus = ps.sumOf { p -> p.tasks.filter { it.category.isFocus && it.result != null && it.result.outcome != Outcome.MISSED }.sumOf { it.result!!.measuredMin } }
    val bestDay = ps.filter { completion(it) != null }.maxByOrNull { completion(it)!! }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile("Locked in", "$locked/${ps.size}", Modifier.weight(1f))
        SummaryTile("Avg done", if (comps.isEmpty()) "–" else "${(comps.average() * 100).roundToInt()}%", Modifier.weight(1f))
        SummaryTile("Focus", if (focus == 0) "–" else Messages.duration(focus), Modifier.weight(1f))
    }
    if (bestDay != null) {
        Panel(tab = "Best day this month", tabColor = Ink.good, sprite = Sprites.badgeSweep, onClick = { Nav.day = bestDay.date }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalDate.parse(bestDay.date).format(LONG_FMT), style = Type.title)
                }
                Text("${((completion(bestDay) ?: 0f) * 100).roundToInt()}%", style = Type.h2.copy(color = Ink.good))
            }
        }
    }
    val notes = ps.filter { it.note.isNotBlank() }.sortedByDescending { it.date }.take(5)
    if (notes.isNotEmpty()) {
        PixelDivider(Sprites.tabPlan)
        notes.forEachIndexed { i, p ->
            SpeechBubble(
                "",
                Modifier.clickable { Nav.day = p.date },
                fromRight = i % 2 == 1,
                speaker = if (p.mood in 1..5) moodSprites[p.mood - 1] else Sprites.climberA,
            ) {
                Text(LocalDate.parse(p.date).format(LONG_FMT), style = Type.label)
                Spacer(Modifier.height(2.dp))
                Text(p.note, style = Type.body.copy(color = Ink.text))
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier) {
    PixelBox(modifier, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column {
            Label(label)
            Spacer(Modifier.height(6.dp))
            CountText(value, Type.h2)
        }
    }
}

/** One past (or current) day in full. */
@Composable
fun DayDetail(dateKey: String, onClose: () -> Unit) {
    val p = Store.state.plan(dateKey)
    val d = LocalDate.parse(dateKey)
    val tasks = p.sortedTasks
    val res = tasks.mapNotNull { it.result }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(if (p.isLocked) "Locked in" else "Not locked in", color = if (p.isLocked) Ink.amber else Ink.faint)
                Spacer(Modifier.height(4.dp))
                Text(d.format(LONG_FMT), style = Type.h1)
            }
            GhostButton("Close", onClick = onClose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Done", completion(p)?.let { "${(it * 100).roundToInt()}%" } ?: "–", Modifier.weight(1f))
            SummaryTile("Efficiency", if (res.isEmpty()) "–" else "${res.map { it.efficiency }.average().roundToInt()}%", Modifier.weight(1f))
            SummaryTile("Moves", "${p.moves}", Modifier.weight(1f))
        }
        p.lockedAt?.let {
            val z = Instant.ofEpochMilli(it).atZone(Clock.zone)
            Text("Plan locked at ${Messages.time(z.hour * 60 + z.minute)} · +${p.lockPoints} points", style = Type.small)
        }
        if (p.wakeAt != null || p.hasSleepPlan) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(Sprites.bed, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Label("Sleep", color = Ink.sky)
                        Text(p.sleptMin?.let { "Slept ${Messages.duration(it)}" } ?: "Sleep not tracked", style = Type.title)
                        Text(
                            listOfNotNull(
                                p.plannedSleepMin?.let { "planned ${Messages.duration(it)}" },
                                p.wakeAt?.let { w -> Instant.ofEpochMilli(w).atZone(Clock.zone).let { "woke ${Messages.time(it.hour * 60 + it.minute)}" } },
                                if (p.wakeAt != null) Messages.morningLine(p.snoozes).trimEnd('.').lowercase() else null,
                            ).joinToString(" · "),
                            style = Type.small,
                        )
                    }
                }
            }
        }
        if (p.meals.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.agnibha.tracker.Meal.entries.forEach { m ->
                    val ate = p.meals[m.name]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelSprite(mealSprite(m), Modifier.size(24.dp), muted = ate != true)
                        Spacer(Modifier.width(4.dp))
                        Text(when (ate) { true -> "ate"; false -> "skipped"; null -> "–" }, style = Type.small.copy(color = when (ate) { true -> Ink.good; false -> Ink.bad; null -> Ink.faint }))
                    }
                }
            }
        }
        if (p.mood in 1..5 || p.note.isNotBlank()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.mood in 1..5) {
                        PixelSprite(moodSprites[p.mood - 1], Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        if (p.mood in 1..5) Label(Messages.moodLabels[p.mood - 1])
                        if (p.note.isNotBlank()) Text(p.note, style = Type.body.copy(color = Ink.text))
                    }
                }
            }
        }
        if (tasks.isEmpty()) {
            Text("No tasks on this day.", style = Type.body)
        } else {
            Label("Tasks")
            val live = Store.isLive(dateKey)
            tasks.forEach { t ->
                val (status, color) = when {
                    t.result?.outcome == Outcome.MISSED -> "Missed" to Ink.bad
                    t.result != null -> "${t.result.efficiency}%" to effColor(t.result.efficiency)
                    else -> "No check-out" to Ink.faint
                }
                TaskRow(t, status, color) {
                    val r = t.result
                    if (r != null) Nav.result = t to r
                    else if (live) sheetFor = t.id
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    sheetFor?.let { id -> TaskSheet(dateKey, id) { sheetFor = null } }
}
