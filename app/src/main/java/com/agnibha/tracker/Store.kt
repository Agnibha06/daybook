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
    const val DAY_END_MIN = 23 * 60 + 59

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

    private fun putPlan(dateKey: String, change: (DayPlan) -> DayPlan) = update { s ->
        s.copy(plans = s.plans + (dateKey to change(s.plan(dateKey))))
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

    /**
     * A day is live once its alerts are running: it was locked in, or it is today and the
     * window has closed, or it is in the past. Live days have a limited number of moves.
     */
    fun isLive(dateKey: String, s: AppState = state): Boolean {
        val d = LocalDate.parse(dateKey)
        val today = Clock.today()
        return s.plan(dateKey).isLocked || d.isBefore(today) || (d == today && !Clock.inWindow())
    }

    fun canLock(s: AppState = state): Boolean {
        val d = draftDate(s)
        return Clock.inWindow() && d == Clock.today() && s.plan(Clock.key(d)).tasks.isNotEmpty()
    }

    fun upsertTask(dateKey: String, task: Task) = putPlan(dateKey) { p ->
        p.copy(tasks = p.tasks.filter { it.id != task.id } + task)
    }

    /** Deleting is only allowed before the day goes live. Returns false when refused. */
    fun deleteTask(ctx: Context, dateKey: String, id: String): Boolean {
        if (isLive(dateKey)) return false
        putPlan(dateKey) { p -> p.copy(tasks = p.tasks.filter { it.id != id }) }
        Alarms.cancelTask(ctx, dateKey, id)
        return true
    }

    fun newTaskId(): String = UUID.randomUUID().toString().take(8)

    // ---------- Moves: postpone or retime, twice a day ----------

    sealed interface MoveResult {
        data object Ok : MoveResult
        data object NoMovesLeft : MoveResult
        data object NotAllowed : MoveResult
    }

    fun movesLeft(dateKey: String): Int = if (isLive(dateKey)) state.plan(dateKey).movesLeft else MAX_MOVES

    /** Change a task's time. On a live day this uses one of the day's two moves. */
    fun retime(ctx: Context, dateKey: String, id: String, newStart: Int, newEnd: Int): MoveResult {
        val t = findTask(dateKey, id) ?: return MoveResult.NotAllowed
        if (t.result != null || newEnd <= newStart || newStart < 0 || newEnd > Clock.DAY_END_MIN) return MoveResult.NotAllowed
        if (newStart == t.startMin && newEnd == t.endMin) return MoveResult.Ok
        val live = isLive(dateKey)
        if (live && state.plan(dateKey).movesLeft <= 0) return MoveResult.NoMovesLeft
        Alarms.cancelTask(ctx, dateKey, id)
        Alerts.cancelTask(ctx, dateKey, id)
        putPlan(dateKey) { p ->
            val moved = t.copy(
                startMin = newStart,
                endMin = newEnd,
                moved = t.moved + if (live) 1 else 0,
                plannedStart = if (live) (t.plannedStart ?: t.startMin) else t.plannedStart,
            )
            p.copy(tasks = p.tasks.filter { it.id != id } + moved, moves = p.moves + if (live) 1 else 0)
        }
        Alarms.scheduleTasks(ctx, dateKey)
        return MoveResult.Ok
    }

    /** Push a task by [minutes], keeping its length. */
    fun push(ctx: Context, dateKey: String, id: String, minutes: Int): MoveResult {
        val t = findTask(dateKey, id) ?: return MoveResult.NotAllowed
        val start = maxOf(t.startMin, if (dateKey == Clock.todayKey()) Clock.minutesNow() else 0) + minutes
        val end = start + t.durationMin
        if (end > Clock.DAY_END_MIN) return MoveResult.NotAllowed
        return retime(ctx, dateKey, id, start, end)
    }

    /** Give a running task more time. Uses a move on a live day. */
    fun extend(ctx: Context, dateKey: String, id: String, minutes: Int): MoveResult {
        val t = findTask(dateKey, id) ?: return MoveResult.NotAllowed
        return retime(ctx, dateKey, id, t.startMin, minOf(t.endMin + minutes, Clock.DAY_END_MIN))
    }

    /** Move a task to the next day's draft. Uses a move on a live day. */
    fun postponeToTomorrow(ctx: Context, dateKey: String, id: String): MoveResult {
        val t = findTask(dateKey, id) ?: return MoveResult.NotAllowed
        if (t.result != null) return MoveResult.NotAllowed
        val live = isLive(dateKey)
        if (live && state.plan(dateKey).movesLeft <= 0) return MoveResult.NoMovesLeft
        val next = Clock.key(LocalDate.parse(dateKey).plusDays(1))
        Alarms.cancelTask(ctx, dateKey, id)
        Alerts.cancelTask(ctx, dateKey, id)
        update { s ->
            val p = s.plan(dateKey)
            val n = s.plan(next)
            val carried = t.copy(id = newTaskId(), moved = t.moved + 1, carriedFrom = dateKey)
            s.copy(
                plans = s.plans +
                    (dateKey to p.copy(tasks = p.tasks.filter { it.id != id }, moves = p.moves + if (live) 1 else 0)) +
                    (next to n.copy(tasks = n.tasks + carried)),
            )
        }
        return MoveResult.Ok
    }

    // ---------- Routines and copying ----------

    /** Copy the most recent earlier plan's tasks (without results) into the draft. */
    fun copyLastPlan(dateKey: String): Int {
        val target = LocalDate.parse(dateKey)
        val source = state.plans.values
            .filter { LocalDate.parse(it.date).isBefore(target) && it.tasks.isNotEmpty() }
            .maxByOrNull { it.date } ?: return 0
        return addItems(dateKey, source.tasks.map { RoutineItem(it.title, it.category, it.plannedStart ?: it.startMin, (it.plannedStart ?: it.startMin) + it.durationMin) })
    }

    fun routineFor(dateKey: String): List<RoutineItem> = state.routines[LocalDate.parse(dateKey).dayOfWeek.value] ?: emptyList()

    fun loadRoutine(dateKey: String): Int = addItems(dateKey, routineFor(dateKey))

    fun saveRoutine(dateKey: String) {
        val day = LocalDate.parse(dateKey).dayOfWeek.value
        val items = state.plan(dateKey).sortedTasks.map { RoutineItem(it.title, it.category, it.startMin, it.endMin) }
        update { s -> s.copy(routines = s.routines + (day to items)) }
    }

    fun clearRoutine(day: Int) = update { s -> s.copy(routines = s.routines - day) }

    /** Adds items that aren't already in the plan (same title and start). Returns how many were added. */
    private fun addItems(dateKey: String, items: List<RoutineItem>): Int {
        val existing = state.plan(dateKey).tasks.map { it.title.lowercase() to it.startMin }.toSet()
        val fresh = items.filter { (it.title.lowercase() to it.startMin) !in existing }
        if (fresh.isEmpty()) return 0
        putPlan(dateKey) { p ->
            p.copy(tasks = p.tasks + fresh.map { Task(newTaskId(), it.title, it.category, it.startMin, it.endMin) })
        }
        return fresh.size
    }

    // ---------- Lock-in and reflection ----------

    fun setReflection(dateKey: String, note: String, mood: Int) = putPlan(dateKey) { it.copy(note = note.trim(), mood = mood) }

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
        Alerts.cancel(ctx, Kind.PLAN_REMIND, null, null)
        Alerts.cancel(ctx, Kind.PLAN_LAST_CALL, null, null)
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
    fun markBadgesSeen(ids: Collection<String>) = update { it.copy(badgesSeen = it.badgesSeen + ids) }

    // ---------- Backup ----------

    fun exportJson(): String = Codec.encode(state)

    /** Replace everything with a backup. Returns false if the file isn't a Daybook backup. */
    fun importJson(ctx: Context, text: String): Boolean {
        val decoded = runCatching { Codec.decode(text) }.getOrNull() ?: return false
        if (decoded.username.isBlank() && decoded.plans.isEmpty()) return false
        update { decoded.copy(setupDone = true) }
        Alarms.scheduleAll(ctx)
        return true
    }
}
