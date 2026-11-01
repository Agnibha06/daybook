package com.agnibha.tracker

import java.time.Instant
import java.time.LocalDate

data class Badge(
    val id: String,
    val name: String,
    val how: String,
    /** 0..1 progress toward earning it. 1 = earned. */
    val progress: Float,
    val detail: String,
) {
    val earned: Boolean get() = progress >= 1f
}

object Badges {
    private fun frac(have: Int, need: Int): Float = (have.toFloat() / need).coerceIn(0f, 1f)

    fun all(s: AppState): List<Badge> {
        val plans = s.plans.values.sortedBy { it.date }
        val best = maxOf(s.bestStreak, Store.streak(s))
        val checkIns = plans.count { it.isLocked }
        val results = plans.flatMap { p -> p.tasks.mapNotNull { t -> t.result?.let { Triple(p, t, it) } } }

        val earlyLocks = plans.count { p ->
            p.lockedAt?.let { Instant.ofEpochMilli(it).atZone(Clock.zone).let { z -> z.hour == 0 && z.minute < 30 } } == true
        }

        val ghost = results.count { (_, t, r) ->
            t.category.isFocus && r.outcome == Outcome.DONE && r.unlocks == 0 && r.measuredMin >= 60
        }

        val deepDays = plans.count { p ->
            val focus = p.tasks.mapNotNull { t -> t.result?.takeIf { t.category.isFocus && it.outcome != Outcome.MISSED } }
            focus.sumOf { it.measuredMin } >= 180 && focus.isNotEmpty() && focus.map { it.efficiency }.average() >= 80
        }

        val sweeps = plans.count { p -> p.tasks.size >= 3 && p.tasks.all { it.result?.outcome == Outcome.DONE } }

        // Longest run of focus blocks at 90%+ in the order they happened.
        val focusOrdered = results.filter { it.second.category.isFocus }
            .sortedWith(compareBy({ it.first.date }, { it.second.startMin }))
        var run = 0
        var bestRun = 0
        focusOrdered.forEach { (_, _, r) ->
            run = if (r.efficiency >= 90 && r.outcome == Outcome.DONE) run + 1 else 0
            bestRun = maxOf(bestRun, run)
        }

        // Longest run of consecutive days at 80%+ completion.
        fun completion(p: DayPlan): Float {
            if (p.tasks.isEmpty()) return 0f
            return p.tasks.sumOf {
                when (it.result?.outcome) { Outcome.DONE -> 1.0; Outcome.PARTIAL -> 0.5; else -> 0.0 }
            }.toFloat() / p.tasks.size
        }
        var dayRun = 0
        var bestDayRun = 0
        var prev: LocalDate? = null
        plans.filter { it.tasks.isNotEmpty() }.forEach { p ->
            val d = LocalDate.parse(p.date)
            val ok = completion(p) >= 0.8f
            dayRun = if (ok && prev != null && d == prev!!.plusDays(1)) dayRun + 1 else if (ok) 1 else 0
            bestDayRun = maxOf(bestDayRun, dayRun)
            prev = d
        }

        val steadyDays = plans.count { p ->
            p.isLocked && p.moves == 0 && p.tasks.isNotEmpty() && p.tasks.all { it.result != null } &&
                LocalDate.parse(p.date).isBefore(Clock.today())
        }

        val points = s.totalPoints

        return listOf(
            Badge("first", "First step", "Lock in your first plan", frac(checkIns, 1), "$checkIns check-ins"),
            Badge("week", "Camp one", "Reach a 7-day streak", frac(best, 7), "best streak $best"),
            Badge("fortnight", "Two flags", "Reach a 14-day streak", frac(best, 14), "best streak $best"),
            Badge("month", "Summit", "Reach a 30-day streak", frac(best, 30), "best streak $best"),
            Badge("early", "Early planner", "Lock in before 12:30 AM on 5 nights", frac(earlyLocks, 5), "$earlyLocks of 5"),
            Badge("ghost", "Ghost mode", "Finish an hour-long focus block with zero unlocks", frac(ghost, 1), "$ghost so far"),
            Badge("deep", "Deep diver", "3 hours of focus in a day at 80%+", frac(deepDays, 1), "$deepDays days"),
            Badge("sweep", "Clean sweep", "Finish every task in a day (3 or more)", frac(sweeps, 1), "$sweeps days"),
            Badge("shield", "Untouchable", "5 focus blocks in a row at 90%+", frac(bestRun, 5), "best run $bestRun"),
            Badge("iron", "Iron week", "7 days in a row at 80%+ completion", frac(bestDayRun, 7), "best run $bestDayRun"),
            Badge("steady", "No moves", "7 locked days with no moves and every task checked out", frac(steadyDays, 7), "$steadyDays of 7"),
            Badge("coin", "Thousand", "Earn 1,000 points", frac(points, 1000), "$points points"),
        )
    }

    /** Badges earned but not yet announced. */
    fun fresh(s: AppState): List<Badge> = all(s).filter { it.earned && it.id !in s.badgesSeen }
}
