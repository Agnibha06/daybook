package com.agnibha.tracker

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Planning-window and date rules, in one place. */
object Clock {
    /** The window for locking in a plan runs from 12:00 AM until this hour. */
    const val WINDOW_END_HOUR = 3
    const val REMIND_HOUR = 23
    const val REMIND_MIN = 50
    const val LAST_CALL_HOUR = 2
    const val LAST_CALL_MIN = 30

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun now(): LocalDateTime = LocalDateTime.now()
    fun today(): LocalDate = LocalDate.now()
    fun key(d: LocalDate): String = d.toString()
    fun todayKey(): String = key(today())

    fun inWindow(t: LocalDateTime = now()): Boolean = t.hour < WINDOW_END_HOUR

    fun epoch(date: LocalDate, minutes: Int): Long =
        date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()

    fun epoch(dateKey: String, minutes: Int): Long = epoch(LocalDate.parse(dateKey), minutes)

    fun minutesNow(t: LocalDateTime = now()): Int = t.hour * 60 + t.minute

    /** Millis until the 3:00 AM deadline (only meaningful inside the window). */
    fun millisToDeadline(): Long = epoch(today(), WINDOW_END_HOUR * 60) - System.currentTimeMillis()

    /** Millis until the window opens at the next midnight. */
    fun millisToWindow(): Long = epoch(today().plusDays(1), 0) - System.currentTimeMillis()
}

object Store {
    private var file: File? = null

    var state by mutableStateOf(AppState())
        private set

    /** Bumped whenever the app comes back to the foreground, so screens re-check permissions. */
    var resumeTick by mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "daybook.json")
        file = f
        state = runCatching { if (f.exists()) Codec.decode(f.readText()) else AppState() }.getOrDefault(AppState())
    }

    fun update(change: (AppState) -> AppState) {
        state = change(state)
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Codec.encode(state))
            tmp.renameTo(f)
        }
    }

    // ---------- Streak ----------

    /** Current streak. A day counts if its plan was locked in before 3:00 AM. */
    fun streak(s: AppState = state, t: LocalDateTime = Clock.now()): Int {
        val today = t.toLocalDate()
        var d = when {
            s.plan(Clock.key(today)).isLocked -> today
            t.hour < Clock.WINDOW_END_HOUR -> today.minusDays(1) // still time tonight
            else -> return 0
        }
        var n = 0
        while (s.plan(Clock.key(d)).isLocked) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** Tonight's check-in is still pending and the streak would break at 3:00 AM. */
    fun streakAtRisk(s: AppState = state): Boolean =
        Clock.inWindow() && !s.plan(Clock.todayKey()).isLocked

    fun totalCheckIns(s: AppState = state): Int = s.plans.values.count { it.isLocked }

    // ---------- Planning ----------

    /** The date the Plan tab is drafting: today while tonight's window is open and unused, else tomorrow. */
    fun draftDate(s: AppState = state): LocalDate {
        val t = Clock.now()
        return if (Clock.inWindow(t) && !s.plan(Clock.key(t.toLocalDate())).isLocked) t.toLocalDate()
        else t.toLocalDate().plusDays(1)
    }

    fun canLock(s: AppState = state): Boolean {
        val d = draftDate(s)
        return Clock.inWindow() && d == Clock.today() && s.plan(Clock.key(d)).tasks.isNotEmpty()
    }

    fun upsertTask(dateKey: String, task: Task) = update { s ->
        val p = s.plan(dateKey)
        val tasks = p.tasks.filter { it.id != task.id } + task
        s.copy(plans = s.plans + (dateKey to p.copy(tasks = tasks)))
    }

    fun deleteTask(dateKey: String, id: String) = update { s ->
        val p = s.plan(dateKey)
        s.copy(plans = s.plans + (dateKey to p.copy(tasks = p.tasks.filter { it.id != id })))
    }

    fun newTaskId(): String = UUID.randomUUID().toString().take(8)

    /** Copy the most recent earlier plan's tasks (without results) into the draft. */
    fun copyLastPlan(dateKey: String): Boolean {
        val target = LocalDate.parse(dateKey)
        val source = state.plans.values
            .filter { LocalDate.parse(it.date).isBefore(target) && it.tasks.isNotEmpty() }
            .maxByOrNull { it.date } ?: return false
        update { s ->
            val p = s.plan(dateKey)
            val copied = source.tasks.map { it.copy(id = newTaskId(), result = null) }
            s.copy(plans = s.plans + (dateKey to p.copy(tasks = p.tasks + copied)))
        }
        return true
    }

    /** Lock tonight's plan. Returns points earned, or null if not allowed right now. */
    fun lockPlan(ctx: Context): Int? {
        if (!canLock()) return null
        val key = Clock.todayKey()
        val streakAfter = streak() + 1
        val pts = Scoring.lockPoints(streakAfter)
        update { s ->
            val p = s.plan(key)
            s.copy(
                plans = s.plans + (key to p.copy(lockedAt = System.currentTimeMillis(), lockPoints = pts)),
                bestStreak = maxOf(s.bestStreak, streakAfter),
            )
        }
        Alarms.scheduleAll(ctx)
        if (streakAfter % 7 == 0) Alerts.milestone(ctx, streakAfter)
        return pts
    }

    // ---------- Check-out ----------

    fun findTask(dateKey: String, id: String): Task? = state.plans[dateKey]?.tasks?.firstOrNull { it.id == id }

    /**
     * Close a task: measure phone use during it, score it, save it.
     * [early] = finished before the planned end; usage is measured up to now.
     */
    fun checkOut(ctx: Context, dateKey: String, id: String, outcome: Outcome, early: Boolean): TaskResult? {
        val task = findTask(dateKey, id) ?: return null
        val start = Clock.epoch(dateKey, task.startMin)
        val plannedEnd = Clock.epoch(dateKey, task.endMin)
        val end = if (early) minOf(System.currentTimeMillis(), plannedEnd) else plannedEnd
        val window = if (end > start) Usage.measure(ctx, start, end, state.distracting) else null
        val measuredMin = ((end - start).coerceAtLeast(0L) / 60000L).toInt()
        val eff = Scoring.efficiency(task.category, outcome, window, measuredMin)
        val result = TaskResult(
            outcome = outcome,
            efficiency = eff,
            unlocks = window?.unlocks,
            screenMin = window?.screenMin,
            distractMin = window?.distractMin,
            distractApps = window?.distractApps ?: emptyMap(),
            points = Scoring.taskPoints(outcome, eff),
            checkedOutAt = System.currentTimeMillis(),
            measuredMin = measuredMin,
            endedEarly = early,
        )
        upsertTask(dateKey, task.copy(result = result))
        Alarms.cancelTask(ctx, dateKey, id)
        Alerts.cancelTask(ctx, dateKey, id)
        return result
    }

    fun markFlashShown() = update { it.copy(lastFlashDate = Clock.todayKey()) }
    fun markMilestoneShown(n: Int) = update { it.copy(lastMilestoneShown = n) }
}
