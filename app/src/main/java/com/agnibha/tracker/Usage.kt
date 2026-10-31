package com.agnibha.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reads Android's usage history to see what the phone was doing during a task. */
object Usage {
    // Raw event codes, so the logic doesn't depend on which SDK named them.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val ACTIVITY_STOPPED = 23
    private const val SCREEN_INTERACTIVE = 15
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_HIDDEN = 18 // = phone unlocked

    data class Window(
        val unlocks: Int,
        val screenMin: Int,
        val distractMin: Int,
        val distractApps: Map<String, Int>,
    )

    data class AppUse(val pkg: String, val label: String, val minutes: Int)

    fun hasAccess(ctx: Context): Boolean {
        val aom = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    private fun ignored(ctx: Context): Set<String> {
        val home = runCatching {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        return setOfNotNull(ctx.packageName, home, "com.android.systemui", "android")
    }

    /** Measure [start, end] (epoch millis). Returns null without usage access. */
    fun measure(ctx: Context, start: Long, end: Long, distracting: Set<String>): Window? {
        if (!hasAccess(ctx) || end <= start) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val skip = ignored(ctx)
        // Look back 3 hours so an app already open when the task began is caught.
        val events = runCatching { usm.queryEvents(start - 3 * 3600_000L, end) }.getOrNull() ?: return null
        val e = UsageEvents.Event()

        fun clip(a: Long, b: Long): Long = max(0L, min(b, end) - max(a, start))

        var unlocks = 0
        var screenMs = 0L
        var screenOnSince: Long? = null
        val openSince = HashMap<String, Long>()
        val appMs = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val s = openSince.remove(pkg) ?: return
            appMs[pkg] = (appMs[pkg] ?: 0L) + clip(s, at)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val t = e.timeStamp
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                ACTIVITY_RESUMED -> if (pkg !in skip && pkg !in openSince) openSince[pkg] = t
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> close(pkg, t)
                SCREEN_INTERACTIVE -> if (screenOnSince == null) screenOnSince = t
                SCREEN_NON_INTERACTIVE -> {
                    screenOnSince?.let { screenMs += clip(it, t) }
                    screenOnSince = null
                    openSince.keys.toList().forEach { close(it, t) }
                }
                KEYGUARD_HIDDEN -> if (t in start..end) unlocks++
            }
        }
        screenOnSince?.let { screenMs += clip(it, end) }
        openSince.keys.toList().forEach { close(it, end) }

        val distractApps = appMs
            .filter { it.key in distracting && it.value >= 30_000L }
            .map { label(ctx, it.key) to (it.value / 60000.0).roundToInt().coerceAtLeast(1) }
            .sortedByDescending { it.second }
            .toMap()

        return Window(
            unlocks = unlocks,
            screenMin = (screenMs / 60000.0).roundToInt(),
            distractMin = distractApps.values.sum(),
            distractApps = distractApps,
        )
    }

    /** Apps you used most in the last week, for picking which ones count as distracting. */
    fun recentApps(ctx: Context): List<AppUse> {
        if (!hasAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val skip = ignored(ctx)
        val pm = ctx.packageManager
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7 * 86_400_000L, now) }
            .getOrNull() ?: return emptyList()
        return stats
            .groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { it.totalTimeInForeground } }
            .filter { (pkg, ms) -> pkg !in skip && ms > 5 * 60_000L && pm.getLaunchIntentForPackage(pkg) != null }
            .map { (pkg, ms) -> AppUse(pkg, label(ctx, pkg), (ms / 60000L).toInt()) }
            .sortedByDescending { it.minutes }
            .take(30)
    }
}

object Scoring {
    /**
     * Efficiency, 0–100.
     * Starts from how much of the task you say you finished, then subtracts for
     * time on distracting apps and for unlocking more than once per 45 minutes.
     * Breaks are never penalised; workouts and music only half as much.
     */
    fun efficiency(cat: Category, outcome: Outcome, w: Usage.Window?, minutes: Int): Int {
        val done = when (outcome) { Outcome.DONE -> 1.0; Outcome.PARTIAL -> 0.6; Outcome.MISSED -> 0.0 }
        if (w == null) return (done * 100).roundToInt()
        val d = max(1, minutes).toDouble()
        val distractFrac = min(1.0, w.distractMin / d)
        val allowance = max(1, kotlin.math.ceil(d / 45.0).toInt())
        val unlockPenalty = max(0, w.unlocks - allowance) * 4.0
        val raw = 100.0 - cat.focusWeight * (distractFrac * 100.0 + unlockPenalty)
        return (done * raw.coerceIn(0.0, 100.0)).roundToInt()
    }

    fun taskPoints(outcome: Outcome, eff: Int): Int = when (outcome) {
        Outcome.DONE -> 10 + eff / 10
        Outcome.PARTIAL -> 5 + eff / 20
        Outcome.MISSED -> 0
    }

    /** Points for the nightly check-in. Longer streaks pay a little more, capped at day 30. */
    fun lockPoints(streakAfter: Int): Int = 20 + min(streakAfter, 30)

    const val POINTS_PER_LEVEL = 300
    fun level(points: Int): Int = points / POINTS_PER_LEVEL + 1
}
