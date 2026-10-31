package com.agnibha.tracker

import org.json.JSONArray
import org.json.JSONObject

/** What kind of block a task is. focusWeight decides how much phone use costs it. */
enum class Category(val label: String, val focusWeight: Float, val color: Long) {
    STUDY("Study", 1f, 0xFF7FA7E8),
    CLASS("Class", 1f, 0xFF9C8FE0),
    LAB("Lab", 1f, 0xFF5FB8B0),
    PROJECT("Project", 1f, 0xFFE0A06A),
    WORKOUT("Workout", 0.5f, 0xFFE07A6F),
    MUSIC("Music", 0.5f, 0xFFD58FC0),
    ERRAND("Errand", 0.3f, 0xFFA8B07A),
    BREAK("Break", 0f, 0xFF7D8694);

    val isFocus: Boolean get() = focusWeight >= 1f
}

enum class Outcome(val label: String) { DONE("Done"), PARTIAL("Partly"), MISSED("Missed") }

data class TaskResult(
    val outcome: Outcome,
    val efficiency: Int,
    /** Phone numbers are null when usage access was not granted. */
    val unlocks: Int?,
    val screenMin: Int?,
    val distractMin: Int?,
    /** Distracting app label -> minutes used during the task. */
    val distractApps: Map<String, Int>,
    val points: Int,
    val checkedOutAt: Long,
    val measuredMin: Int,
    val endedEarly: Boolean,
)

data class Task(
    val id: String,
    val title: String,
    val category: Category,
    /** Minutes after midnight of the plan date. */
    val startMin: Int,
    val endMin: Int,
    val result: TaskResult? = null,
) {
    val durationMin: Int get() = endMin - startMin
}

data class DayPlan(
    val date: String, // yyyy-MM-dd
    val tasks: List<Task> = emptyList(),
    /** Set when the plan was locked inside the 12:00–3:00 AM window. This is the daily check-in. */
    val lockedAt: Long? = null,
    val lockPoints: Int = 0,
) {
    val isLocked: Boolean get() = lockedAt != null
    val sortedTasks: List<Task> get() = tasks.sortedBy { it.startMin }
}

data class AppState(
    val username: String = "",
    val plans: Map<String, DayPlan> = emptyMap(),
    val bestStreak: Int = 0,
    val distracting: Set<String> = DEFAULT_DISTRACTING,
    /** Date on which the daily streak flash was last shown. */
    val lastFlashDate: String? = null,
    /** Streak length for which the weekly milestone pop-up was last shown. */
    val lastMilestoneShown: Int = 0,
    val setupDone: Boolean = false,
) {
    val totalPoints: Int
        get() = plans.values.sumOf { p -> p.lockPoints + p.tasks.sumOf { it.result?.points ?: 0 } }

    fun plan(date: String): DayPlan = plans[date] ?: DayPlan(date)
}

val DEFAULT_DISTRACTING: Set<String> = setOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.snapchat.android",
    "com.facebook.katana",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.netflix.mediaclient",
    "in.startv.hotstar",
    "com.jio.media.ondemand",
    "com.amazon.avod.thirdpartyclient",
    "in.mohalla.video",
    "in.mohalla.sharechat",
    "com.eterno.shortvideos",
    "com.pubg.imobile",
    "com.dts.freefireth",
)

// ---------- JSON ----------

object Codec {
    fun encode(s: AppState): String {
        val o = JSONObject()
        o.put("username", s.username)
        o.put("bestStreak", s.bestStreak)
        o.put("lastFlashDate", s.lastFlashDate ?: JSONObject.NULL)
        o.put("lastMilestoneShown", s.lastMilestoneShown)
        o.put("setupDone", s.setupDone)
        o.put("distracting", JSONArray(s.distracting.toList()))
        val plans = JSONObject()
        s.plans.forEach { (k, p) -> plans.put(k, plan(p)) }
        o.put("plans", plans)
        return o.toString()
    }

    fun decode(text: String): AppState {
        val o = JSONObject(text)
        val plans = mutableMapOf<String, DayPlan>()
        val po = o.optJSONObject("plans") ?: JSONObject()
        po.keys().forEach { k -> plans[k] = plan(po.getJSONObject(k)) }
        val dist = o.optJSONArray("distracting")
        return AppState(
            username = o.optString("username", ""),
            plans = plans,
            bestStreak = o.optInt("bestStreak", 0),
            distracting = if (dist == null) DEFAULT_DISTRACTING else (0 until dist.length()).map { dist.getString(it) }.toSet(),
            lastFlashDate = if (o.isNull("lastFlashDate")) null else o.optString("lastFlashDate"),
            lastMilestoneShown = o.optInt("lastMilestoneShown", 0),
            setupDone = o.optBoolean("setupDone", false),
        )
    }

    private fun plan(p: DayPlan): JSONObject = JSONObject().apply {
        put("date", p.date)
        put("lockedAt", p.lockedAt ?: JSONObject.NULL)
        put("lockPoints", p.lockPoints)
        put("tasks", JSONArray().apply { p.tasks.forEach { put(task(it)) } })
    }

    private fun plan(o: JSONObject): DayPlan {
        val arr = o.optJSONArray("tasks") ?: JSONArray()
        return DayPlan(
            date = o.getString("date"),
            tasks = (0 until arr.length()).map { task(arr.getJSONObject(it)) },
            lockedAt = if (o.isNull("lockedAt")) null else o.optLong("lockedAt"),
            lockPoints = o.optInt("lockPoints", 0),
        )
    }

    private fun task(t: Task): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("category", t.category.name)
        put("startMin", t.startMin)
        put("endMin", t.endMin)
        put("result", t.result?.let { result(it) } ?: JSONObject.NULL)
    }

    private fun task(o: JSONObject): Task = Task(
        id = o.getString("id"),
        title = o.getString("title"),
        category = runCatching { Category.valueOf(o.getString("category")) }.getOrDefault(Category.STUDY),
        startMin = o.getInt("startMin"),
        endMin = o.getInt("endMin"),
        result = if (o.isNull("result")) null else result(o.getJSONObject("result")),
    )

    private fun result(r: TaskResult): JSONObject = JSONObject().apply {
        put("outcome", r.outcome.name)
        put("efficiency", r.efficiency)
        put("unlocks", r.unlocks ?: JSONObject.NULL)
        put("screenMin", r.screenMin ?: JSONObject.NULL)
        put("distractMin", r.distractMin ?: JSONObject.NULL)
        put("distractApps", JSONObject().apply { r.distractApps.forEach { (k, v) -> put(k, v) } })
        put("points", r.points)
        put("checkedOutAt", r.checkedOutAt)
        put("measuredMin", r.measuredMin)
        put("endedEarly", r.endedEarly)
    }

    private fun result(o: JSONObject): TaskResult {
        val apps = mutableMapOf<String, Int>()
        o.optJSONObject("distractApps")?.let { a -> a.keys().forEach { k -> apps[k] = a.getInt(k) } }
        fun optIntOrNull(key: String): Int? = if (o.isNull(key)) null else o.optInt(key)
        return TaskResult(
            outcome = runCatching { Outcome.valueOf(o.getString("outcome")) }.getOrDefault(Outcome.MISSED),
            efficiency = o.optInt("efficiency", 0),
            unlocks = optIntOrNull("unlocks"),
            screenMin = optIntOrNull("screenMin"),
            distractMin = optIntOrNull("distractMin"),
            distractApps = apps,
            points = o.optInt("points", 0),
            checkedOutAt = o.optLong("checkedOutAt", 0L),
            measuredMin = o.optInt("measuredMin", 0),
            endedEarly = o.optBoolean("endedEarly", false),
        )
    }
}
