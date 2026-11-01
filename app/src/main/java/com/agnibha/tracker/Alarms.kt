package com.agnibha.tracker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime

enum class Kind { PLAN_REMIND, PLAN_LAST_CALL, PLAN_DEADLINE, TASK_SOON, TASK_END, TEST, WAKE, BEDTIME, MEAL }

object Alarms {
    const val EXTRA_KIND = "kind"
    const val EXTRA_DATE = "date"
    const val EXTRA_TASK = "task"
    const val SOON_MINUTES = 10

    fun code(kind: Kind, date: String?, taskId: String?): Int = when (kind) {
        Kind.PLAN_REMIND -> 101
        Kind.PLAN_LAST_CALL -> 102
        Kind.PLAN_DEADLINE -> 103
        Kind.TEST -> 104
        Kind.WAKE -> 105
        Kind.BEDTIME -> 106
        Kind.MEAL -> 110 + (runCatching { Meal.valueOf(taskId ?: "").ordinal }.getOrDefault(0))
        else -> "$kind|$date|$taskId".hashCode()
    }

    private fun intent(ctx: Context, kind: Kind, date: String?, taskId: String?): Intent =
        Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.agnibha.tracker.$kind")
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_DATE, date)
            .putExtra(EXTRA_TASK, taskId)

    private fun pending(ctx: Context, kind: Kind, date: String?, taskId: String?, noCreate: Boolean = false): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT)
        return PendingIntent.getBroadcast(ctx, code(kind, date, taskId), intent(ctx, kind, date, taskId), flags)
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (canExact(ctx)) {
                val show = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                // Alarm-clock alarms are exact and fire even in deep sleep.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun nextAt(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var t = LocalDate.now().atTime(hour, minute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(Clock.zone).toInstant().toEpochMilli()
    }

    fun scheduleAll(ctx: Context) {
        scheduleDaily(ctx)
        scheduleTasks(ctx, Clock.todayKey())
        scheduleSleep(ctx)
        scheduleMeals(ctx)
    }

    /**
     * Each meal is asked about once a day at its hour, unless it was already answered.
     * A "remind me later" moves today's question to its remind time.
     */
    fun scheduleMeals(ctx: Context) {
        val now = System.currentTimeMillis()
        val today = Clock.todayKey()
        val tomorrow = Clock.key(Clock.today().plusDays(1))
        val p = Store.state.plan(today)
        Meal.entries.forEach { m ->
            val remind = p.mealRemind[m.name]?.takeIf { it > now && m.name !in p.meals }
            val todayAt = Clock.epoch(today, m.hour * 60).takeIf { it > now && m.name !in p.meals }
            val (key, at) = when {
                remind != null -> today to remind
                todayAt != null -> today to todayAt
                else -> tomorrow to Clock.epoch(tomorrow, m.hour * 60)
            }
            pending(ctx, Kind.MEAL, key, m.name)?.let { set(ctx, at, it) }
        }
    }

    /**
     * The next wake alarm and bedtime reminder, from today's or tomorrow's plan.
     * A snooze pushes the wake alarm to its snooze time.
     */
    fun scheduleSleep(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val days = listOf(Clock.today(), Clock.today().plusDays(1)).map { Clock.key(it) }

        val wake = days.mapNotNull { k ->
            val p = Store.state.plan(k)
            val w = p.wakeMin ?: return@mapNotNull null
            if (p.wakeAt != null) return@mapNotNull null
            val at = maxOf(Clock.epoch(k, w), p.snoozeUntil ?: 0L)
            if (at > now) k to at else null
        }.minByOrNull { it.second }
        if (wake != null) pending(ctx, Kind.WAKE, wake.first, null)?.let { set(ctx, wake.second, it) }
        else pending(ctx, Kind.WAKE, null, null, noCreate = true)?.let { am.cancel(it) }

        val bed = if (Store.state.sleepingSince != null) null else days.mapNotNull { k ->
            val at = Store.bedtimeEpoch(k) ?: return@mapNotNull null
            if (at > now && Store.state.plan(k).sleepStart == null) k to at else null
        }.minByOrNull { it.second }
        if (bed != null) pending(ctx, Kind.BEDTIME, bed.first, null)?.let { set(ctx, bed.second, it) }
        else pending(ctx, Kind.BEDTIME, null, null, noCreate = true)?.let { am.cancel(it) }
    }

    fun snooze(ctx: Context, dateKey: String, at: Long) {
        pending(ctx, Kind.WAKE, dateKey, null)?.let { set(ctx, at, it) }
    }

    fun scheduleDaily(ctx: Context) {
        pending(ctx, Kind.PLAN_REMIND, null, null)?.let { set(ctx, nextAt(Clock.REMIND_HOUR, Clock.REMIND_MIN), it) }
        pending(ctx, Kind.PLAN_LAST_CALL, null, null)?.let { set(ctx, nextAt(Clock.LAST_CALL_HOUR, Clock.LAST_CALL_MIN), it) }
        pending(ctx, Kind.PLAN_DEADLINE, null, null)?.let { set(ctx, nextAt(Clock.WINDOW_END_HOUR, 0), it) }
    }

    /** Alerts ring for a day once it is live: locked in, or past 3:00 AM. */
    fun scheduleTasks(ctx: Context, dateKey: String) {
        val plan = Store.state.plan(dateKey)
        val live = plan.isLocked || (dateKey == Clock.todayKey() && !Clock.inWindow())
        if (!live) return
        val now = System.currentTimeMillis()
        plan.tasks.filter { it.result == null }.forEach { t ->
            val start = Clock.epoch(dateKey, t.startMin)
            val end = Clock.epoch(dateKey, t.endMin)
            val soon = start - SOON_MINUTES * 60_000L
            if (soon > now + 5_000L) pending(ctx, Kind.TASK_SOON, dateKey, t.id)?.let { set(ctx, soon, it) }
            if (end > now) pending(ctx, Kind.TASK_END, dateKey, t.id)?.let { set(ctx, end, it) }
        }
    }

    /** Fires a sample alert in 10 seconds so you can check it shows over everything. */
    fun scheduleTest(ctx: Context) {
        pending(ctx, Kind.TEST, null, null)?.let { set(ctx, System.currentTimeMillis() + 10_000L, it) }
    }

    fun cancelTask(ctx: Context, dateKey: String, taskId: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        listOf(Kind.TASK_SOON, Kind.TASK_END).forEach { k ->
            pending(ctx, k, dateKey, taskId, noCreate = true)?.let { am.cancel(it); it.cancel() }
        }
    }
}

object Alerts {
    private const val CH_ALARM = "daybook_alerts_v1"
    private const val CH_INFO = "daybook_streak_v1"
    private const val ID_MILESTONE = 201
    private const val ID_BROKEN = 202
    private val AMBER = 0xFFF0A23B.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarm = NotificationChannel(CH_ALARM, "Task and planning alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen alerts before and after each task, and the nightly planning reminder."
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val info = NotificationChannel(CH_INFO, "Streaks and milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Weekly streak milestones and streak resets."
        }
        nm.createNotificationChannels(listOf(alarm, info))
    }

    fun alarmIntent(ctx: Context, kind: Kind, date: String?, taskId: String?): Intent =
        Intent(ctx, AlarmActivity::class.java)
            .putExtra(Alarms.EXTRA_KIND, kind.name)
            .putExtra(Alarms.EXTRA_DATE, date)
            .putExtra(Alarms.EXTRA_TASK, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun canFullScreen(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /** An alert that takes over the screen and keeps ringing until a button is pressed. */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context, kind: Kind, date: String?, taskId: String?, title: String, body: String, insistent: Boolean) {
        ensureChannels(ctx)
        val id = Alarms.code(kind, date, taskId)
        val full = alarmIntent(ctx, kind, date, taskId)
        val pi = PendingIntent.getActivity(ctx, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(AMBER)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        if (insistent) n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(id, n) }
        // With "Display over other apps" allowed, open the alert on top of whatever is on screen.
        if (Settings.canDrawOverlays(ctx)) runCatching { ctx.startActivity(full) }
    }

    fun cancel(ctx: Context, kind: Kind, date: String?, taskId: String?) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(Alarms.code(kind, date, taskId))
    }

    fun cancelTask(ctx: Context, date: String, taskId: String) {
        cancel(ctx, Kind.TASK_SOON, date, taskId)
        cancel(ctx, Kind.TASK_END, date, taskId)
    }

    @SuppressLint("MissingPermission")
    private fun info(ctx: Context, id: Int, title: String, body: String) {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(AMBER)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }

    fun milestone(ctx: Context, streak: Int) =
        info(ctx, ID_MILESTONE, "Week ${streak / 7} complete", Messages.milestoneBody(streak))

    fun broken(ctx: Context, previous: Int) =
        info(ctx, ID_BROKEN, "Streak reset", Messages.brokenBody(previous))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val kind = runCatching { Kind.valueOf(intent.getStringExtra(Alarms.EXTRA_KIND) ?: "") }.getOrNull() ?: return
        val date = intent.getStringExtra(Alarms.EXTRA_DATE)
        val taskId = intent.getStringExtra(Alarms.EXTRA_TASK)
        val today = Clock.todayKey()
        when (kind) {
            Kind.PLAN_REMIND -> {
                Alarms.scheduleDaily(ctx)
                Alerts.show(ctx, kind, null, null, "Planning opens at 12:00", Messages.remindBody(Store.streak()), insistent = false)
            }
            Kind.PLAN_LAST_CALL -> {
                Alarms.scheduleDaily(ctx)
                if (!Store.state.plan(today).isLocked) {
                    Alerts.show(ctx, kind, null, null, "30 minutes to lock in today", Messages.lastCallBody(Store.streak()), insistent = true)
                }
            }
            Kind.PLAN_DEADLINE -> {
                Alarms.scheduleDaily(ctx)
                Alerts.cancel(ctx, Kind.PLAN_LAST_CALL, null, null)
                if (!Store.state.plan(today).isLocked) {
                    var d = LocalDate.now().minusDays(1)
                    var previous = 0
                    while (Store.state.plan(Clock.key(d)).isLocked) { previous++; d = d.minusDays(1) }
                    if (previous > 0) Alerts.broken(ctx, previous)
                }
                // The day is live now even without a plan; ring for whatever is in it.
                Alarms.scheduleTasks(ctx, today)
            }
            Kind.TEST -> Alerts.show(
                ctx, kind, null, null, "Test alert",
                "This is how task alerts look. It keeps ringing until you press the button.", insistent = true,
            )
            Kind.WAKE -> {
                val d = date ?: today
                val p = Store.state.plan(d)
                if (p.wakeAt == null) {
                    Alerts.show(ctx, kind, d, null, "Wake up", Messages.wakeBody(Store.sleepingMinutes()), insistent = true)
                }
            }
            Kind.BEDTIME -> {
                if (Store.state.sleepingSince == null) {
                    val d = date ?: today
                    Alerts.show(ctx, kind, d, null, "Time to sleep", Messages.bedtimeBody(Store.state.plan(d)), insistent = false)
                }
                Alarms.scheduleSleep(ctx)
            }
            Kind.MEAL -> {
                val meal = runCatching { Meal.valueOf(taskId ?: "") }.getOrNull()
                val d = date ?: today
                if (meal != null && meal.name !in Store.state.plan(d).meals) {
                    Alerts.show(ctx, kind, d, meal.name, "Had ${meal.label.lowercase()}?", Messages.mealBody(meal), insistent = false)
                }
                Alarms.scheduleMeals(ctx)
            }
            Kind.TASK_SOON -> {
                val t = if (date != null && taskId != null) Store.findTask(date, taskId) else null
                if (t != null && t.result == null) {
                    Alerts.show(ctx, kind, date, taskId, "${t.title} in $SOON min", Messages.soonBody(t), insistent = true)
                }
            }
            Kind.TASK_END -> {
                val t = if (date != null && taskId != null) Store.findTask(date, taskId) else null
                if (t != null && t.result == null) {
                    Alerts.cancel(ctx, Kind.TASK_SOON, date, taskId)
                    Alerts.show(ctx, kind, date, taskId, "Time's up: ${t.title}", Messages.endBody(t), insistent = true)
                }
            }
        }
    }

    private companion object {
        const val SOON = Alarms.SOON_MINUTES
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        Alerts.ensureChannels(ctx)
        Alarms.scheduleAll(ctx)
    }
}

fun Context.dismissKeyguardThen(activity: android.app.Activity, then: () -> Unit) {
    val km = getSystemService(KeyguardManager::class.java)
    if (km != null && km.isKeyguardLocked) {
        km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
        })
    } else then()
}
