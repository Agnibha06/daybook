package com.agnibha.tracker

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

data class DayStat(val date: LocalDate, val planned: Int, val doneScore: Float, val avgEff: Int?, val locked: Boolean) {
    val completion: Float get() = if (planned == 0) 0f else doneScore / planned
}

data class CatStat(val cat: Category, val plannedMin: Int, val doneMin: Int, val avgEff: Int?)

data class Summary(
    val planned: Int,
    val checked: Int,
    val completion: Int?,
    val avgEff: Int?,
    val focusMin: Int,
    val minPerUnlock: Int?,
    val distractMin: Int,
    val movesUsed: Int,
    val daysLocked: Int,
)

data class Fact(val head: String, val body: String)

object Insights {
    private data class Item(val date: LocalDate, val task: Task, val plan: DayPlan)

    private fun items(s: AppState, days: Int, end: LocalDate = Clock.today()): List<Item> {
        val from = end.minusDays((days - 1).toLong())
        return s.plans.values.flatMap { p ->
            val d = LocalDate.parse(p.date)
            if (d.isBefore(from) || d.isAfter(end)) emptyList() else p.tasks.map { Item(d, it, p) }
        }
    }

    private fun credit(o: Outcome?): Float = when (o) { Outcome.DONE -> 1f; Outcome.PARTIAL -> 0.5f; else -> 0f }

    fun summary(s: AppState, days: Int = 7): Summary {
        val all = items(s, days)
        val nowMin = Clock.minutesNow()
        val today = Clock.today()
        // Only count tasks whose time has already come.
        val due = all.filter { it.date.isBefore(today) || it.task.endMin <= nowMin || it.task.result != null }
        val checked = due.mapNotNull { it.task.result?.let { r -> it.task to r } }
        val worked = checked.filter { it.second.outcome != Outcome.MISSED }
        val focus = worked.filter { it.first.category.isFocus }
        val focusMin = focus.sumOf { it.second.measuredMin }
        val focusUnlocks = focus.mapNotNull { it.second.unlocks }.sum()
        val measuredFocusMin = focus.filter { it.second.unlocks != null }.sumOf { it.second.measuredMin }
        return Summary(
            planned = due.size,
            checked = checked.size,
            completion = if (due.isEmpty()) null else (due.sumOf { credit(it.task.result?.outcome).toDouble() } / due.size * 100).roundToInt(),
            avgEff = if (checked.isEmpty()) null else checked.map { it.second.efficiency }.average().roundToInt(),
            focusMin = focusMin,
            minPerUnlock = if (focusUnlocks == 0 || measuredFocusMin == 0) null else measuredFocusMin / focusUnlocks,
            distractMin = checked.sumOf { it.second.distractMin ?: 0 },
            movesUsed = (0 until days).sumOf { s.plan(Clock.key(today.minusDays(it.toLong()))).moves },
            daysLocked = (0 until days).count { s.plan(Clock.key(today.minusDays(it.toLong()))).isLocked },
        )
    }

    fun daily(s: AppState, days: Int = 14): List<DayStat> {
        val today = Clock.today()
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val p = s.plan(Clock.key(d))
            val res = p.tasks.mapNotNull { it.result }
            DayStat(
                date = d,
                planned = p.tasks.size,
                doneScore = p.tasks.sumOf { credit(it.result?.outcome).toDouble() }.toFloat(),
                avgEff = if (res.isEmpty()) null else res.map { it.efficiency }.average().roundToInt(),
                locked = p.isLocked,
            )
        }
    }

    /** Average efficiency of worked tasks by the hour they started, 5 AM to midnight. */
    fun byHour(s: AppState, days: Int = 28): List<Pair<Int, Int?>> {
        val worked = items(s, days).filter { it.task.result != null && it.task.result.outcome != Outcome.MISSED }
        return (5..23).map { h ->
            val inHour = worked.filter { it.task.startMin / 60 == h }
            h to if (inHour.isEmpty()) null else inHour.map { it.task.result!!.efficiency }.average().roundToInt()
        }
    }

    fun categories(s: AppState, days: Int = 7): List<CatStat> {
        val all = items(s, days)
        return Category.entries.mapNotNull { c ->
            val inCat = all.filter { it.task.category == c }
            if (inCat.isEmpty()) return@mapNotNull null
            val res = inCat.mapNotNull { it.task.result }
            CatStat(
                cat = c,
                plannedMin = inCat.sumOf { it.task.durationMin },
                doneMin = inCat.sumOf { (it.task.durationMin * credit(it.task.result?.outcome)).roundToInt() },
                avgEff = if (res.isEmpty()) null else res.map { it.efficiency }.average().roundToInt(),
            )
        }.sortedByDescending { it.plannedMin }
    }

    fun apps(s: AppState, days: Int = 7): List<Pair<String, Int>> {
        val totals = HashMap<String, Int>()
        items(s, days).forEach { it.task.result?.distractApps?.forEach { (k, v) -> totals[k] = (totals[k] ?: 0) + v } }
        return totals.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    }

    fun facts(s: AppState): List<Fact> {
        val out = mutableListOf<Fact>()
        val month = items(s, 28)
        val worked = month.filter { it.task.result != null && it.task.result.outcome != Outcome.MISSED }

        // 1. Sharpest and weakest two-hour windows for focus work.
        val focus = worked.filter { it.task.category.isFocus && it.task.result!!.unlocks != null }
        val bins = focus.groupBy { (it.task.startMin / 60) / 2 * 2 }
            .filter { it.value.size >= 2 }
            .mapValues { e -> e.value.map { it.task.result!!.efficiency }.average().roundToInt() }
        if (bins.size >= 2) {
            val best = bins.maxBy { it.value }
            val worst = bins.minBy { it.value }
            if (best.value - worst.value >= 8) {
                out += Fact(
                    "Your sharpest hours: ${Messages.time(best.key * 60)}–${Messages.time((best.key + 2) * 60)}",
                    "Focus blocks starting then average ${best.value}%. From ${Messages.time(worst.key * 60)} it drops to ${worst.value}%. Put the hardest subject in your best window.",
                )
            }
        }

        // 2. Which app eats the most focus time.
        val week = items(s, 7).filter { it.task.category.isFocus }
        val drain = HashMap<String, Int>()
        week.forEach { it.task.result?.distractApps?.forEach { (k, v) -> drain[k] = (drain[k] ?: 0) + v } }
        drain.maxByOrNull { it.value }?.let { (app, min) ->
            if (min >= 10) out += Fact(
                "$app took ${Messages.duration(min)} this week",
                "That's time spent inside your study, class, lab and project blocks. Logging out of it during focus hours is the easiest win on this page.",
            )
        }

        // 3. Unlock rhythm during focus work.
        val fMin = focus.sumOf { it.task.result!!.measuredMin }
        val fUnlocks = focus.sumOf { it.task.result!!.unlocks ?: 0 }
        if (focus.size >= 3) {
            out += if (fUnlocks == 0) Fact(
                "Zero unlocks in ${focus.size} focus blocks",
                "You didn't unlock the phone once during focus work this month. Rare. Protect it.",
            ) else Fact(
                "One unlock every ${maxOf(1, fMin / fUnlocks)} minutes",
                "That's your pace during focus blocks. Each unlock costs a few minutes of getting back into it. Aim for one per 45 minutes.",
            )
        }

        // 4. Does planning late hurt the next day?
        val locked = s.plans.values.filter { it.lockedAt != null && it.tasks.any { t -> t.result != null } }
        val hourOf: (DayPlan) -> Int = { Instant.ofEpochMilli(it.lockedAt!!).atZone(Clock.zone).hour }
        val early = locked.filter { hourOf(it) < 1 }
        val late = locked.filter { hourOf(it) >= 1 }
        fun rate(ps: List<DayPlan>): Int {
            val ts = ps.flatMap { it.tasks }
            return if (ts.isEmpty()) 0 else (ts.sumOf { credit(it.result?.outcome).toDouble() } / ts.size * 100).roundToInt()
        }
        if (early.size >= 2 && late.size >= 2) {
            val a = rate(early)
            val b = rate(late)
            if (kotlin.math.abs(a - b) >= 8) out += Fact(
                if (a > b) "Plan before 1 AM" else "Late plans work for you",
                "Days you planned before 1 AM you finished $a% of tasks; planned after 1 AM, $b%.",
            )
        }

        // 5. Short vs long blocks.
        val short = worked.filter { it.task.durationMin <= 60 && it.task.result!!.unlocks != null }
        val long = worked.filter { it.task.durationMin > 90 && it.task.result!!.unlocks != null }
        if (short.size >= 3 && long.size >= 3) {
            val a = short.map { it.task.result!!.efficiency }.average().roundToInt()
            val b = long.map { it.task.result!!.efficiency }.average().roundToInt()
            if (a - b >= 10) out += Fact(
                "Long blocks leak",
                "Blocks of an hour or less average $a%. Blocks over 90 minutes average $b%. Split long sessions with a short break.",
            ) else if (b - a >= 10) out += Fact(
                "You get better as you go",
                "Blocks over 90 minutes average $b%, short ones $a%. Deep sessions suit you.",
            )
        }

        // 6. Most and least kept category.
        val cats = month.filter { it.task.result != null }.groupBy { it.task.category }.filter { it.value.size >= 3 }
            .mapValues { e -> (e.value.sumOf { credit(it.task.result?.outcome).toDouble() } / e.value.size * 100).roundToInt() }
        if (cats.size >= 2) {
            val hi = cats.maxBy { it.value }
            val lo = cats.minBy { it.value }
            if (hi.value - lo.value >= 15) out += Fact(
                "${lo.key.label} is where plans slip",
                "You finish ${hi.value}% of ${hi.key.label.lowercase()} tasks but only ${lo.value}% of ${lo.key.label.lowercase()}. Plan fewer, shorter ${lo.key.label.lowercase()} blocks.",
            )
        }

        // 7. Do moved tasks still get done?
        val moved = month.filter { it.task.result != null && (it.task.moved > 0 || it.task.carriedFrom != null) }
        val kept = month.filter { it.task.result != null && it.task.moved == 0 && it.task.carriedFrom == null }
        if (moved.size >= 3 && kept.size >= 5) {
            fun pct(xs: List<Item>) = (xs.sumOf { credit(it.task.result?.outcome).toDouble() } / xs.size * 100).roundToInt()
            val a = pct(moved)
            val b = pct(kept)
            out += Fact(
                if (a + 10 < b) "Moved tasks tend to die" else "Moving works for you",
                "Tasks you moved got finished $a% of the time, against $b% for tasks you left alone.",
            )
        }

        // 8. Mood against the day's efficiency.
        val rated = s.plans.values.filter { it.mood > 0 && it.tasks.any { t -> t.result != null } }
        val good = rated.filter { it.mood >= 4 }
        val low = rated.filter { it.mood <= 2 }
        if (good.size >= 2 && low.size >= 2) {
            fun avg(ps: List<DayPlan>) = ps.flatMap { p -> p.tasks.mapNotNull { it.result?.efficiency } }.average().roundToInt()
            val a = avg(good)
            val b = avg(low)
            if (kotlin.math.abs(a - b) >= 8) out += Fact(
                if (a > b) "Good days and focus go together" else "You focus better on rough days",
                "Days you rated 4–5 averaged $a% efficiency. Days rated 1–2 averaged $b%.",
            )
        }

        // 9. Missed tasks that were never checked out.
        val ghosts = month.count { it.date.isBefore(Clock.today()) && it.task.result == null && it.plan.isLocked }
        if (ghosts >= 3) out += Fact(
            "$ghosts tasks never got a check-out",
            "Unchecked tasks don't count toward anything. Tap Done, Partly or Missed when the end alert rings.",
        )
        return out
    }
}
