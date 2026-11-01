package com.agnibha.tracker

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.ui.Block
import com.agnibha.tracker.ui.DaybookTheme
import com.agnibha.tracker.ui.GhostButton
import com.agnibha.tracker.ui.Ink
import com.agnibha.tracker.ui.Label
import com.agnibha.tracker.ui.PixelSprite
import com.agnibha.tracker.ui.PrimaryButton
import com.agnibha.tracker.ui.ResultView
import com.agnibha.tracker.ui.Sprites
import com.agnibha.tracker.ui.Type
import com.agnibha.tracker.ui.sprite
import com.agnibha.tracker.ui.mealSprite
import com.agnibha.tracker.ui.tint
import kotlinx.coroutines.delay

/**
 * The full-screen alert. It shows over the lock screen and other apps, and the
 * back button does nothing: a button has to be pressed to make it go away.
 */
class AlarmActivity : ComponentActivity() {
    private var kind by mutableStateOf<Kind?>(null)
    private var date by mutableStateOf<String?>(null)
    private var taskId by mutableStateOf<String?>(null)
    private var result by mutableStateOf<TaskResult?>(null)
    private var note by mutableStateOf<String?>(null)
    /** Minutes slept once "I'm awake" is pressed; -1 when the night wasn't tracked. */
    private var morning by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* Pick an option instead. */ }
        })
        setContent {
            DaybookTheme {
                Box(Modifier.fillMaxSize().background(Ink.bg).safeDrawingPadding()) {
                    val k = kind
                    val r = result
                    val t = if (date != null && taskId != null) Store.findTask(date!!, taskId!!) else null
                    val screen = when {
                        morning != null -> "morning"
                        note != null -> "note"
                        r != null && t != null -> "result"
                        k == Kind.TASK_SOON && t != null -> "soon"
                        k == Kind.TASK_END && t != null -> "end"
                        k == Kind.PLAN_REMIND || k == Kind.PLAN_LAST_CALL -> "plan"
                        k == Kind.TEST -> "test"
                        k == Kind.WAKE -> "wake"
                        k == Kind.BEDTIME -> "bed"
                        k == Kind.MEAL -> "meal"
                        else -> "none"
                    }
                    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "alarm") { sc ->
                        when (sc) {
                            "note" -> NoteView(note ?: "")
                            "result" -> {
                                if (r != null && t != null) {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                                        ResultView(t, r) { finish() }
                                    }
                                }
                            }
                            "soon" -> t?.let { SoonView(it) }
                            "end" -> t?.let { EndView(it) }
                            "plan" -> k?.let { PlanView(it) }
                            "test" -> TestView()
                            "wake" -> WakeView()
                            "bed" -> BedView()
                            "meal" -> MealView()
                            "morning" -> MorningView(morning ?: -1)
                            else -> LaunchedEffect(Unit) { silence(); finish() }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        kind = runCatching { Kind.valueOf(i.getStringExtra(Alarms.EXTRA_KIND) ?: "") }.getOrNull()
        date = i.getStringExtra(Alarms.EXTRA_DATE)
        taskId = i.getStringExtra(Alarms.EXTRA_TASK)
        result = null
        note = null
        morning = null
    }

    private fun silence() {
        val k = kind ?: return
        Alerts.cancel(this, k, date, taskId)
    }

    private fun close() {
        silence()
        finish()
    }

    @Composable
    private fun Shell(top: String, content: @Composable () -> Unit) {
        var frame by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(300); frame++ } }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(if (frame % 2 == 0) Sprites.climberA else Sprites.climberB, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Label(top, color = Ink.amber)
            }
            content()
        }
    }

    @Composable
    private fun TaskHead(t: Task) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(t.category.sprite(), Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Label(t.category.label, color = t.category.tint())
            }
            Spacer(Modifier.height(12.dp))
            Text(t.title, style = Type.hero)
            Spacer(Modifier.height(8.dp))
            Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)} · ${Messages.duration(t.durationMin)}", style = Type.num.copy(color = Ink.dim))
        }
    }

    @Composable
    private fun MovesLine(d: String) {
        val left = Store.movesLeft(d)
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(MAX_MOVES) { i -> Block(if (i < left) Ink.amber else Ink.line, 10.dp) ; Spacer(Modifier.width(4.dp)) }
            Spacer(Modifier.width(6.dp))
            Text(Messages.movesLeft(left), style = Type.small)
        }
    }

    @Composable
    private fun SoonView(t: Task) = Shell("Starts in ${Alarms.SOON_MINUTES} minutes") {
        Column {
            TaskHead(t)
            Spacer(Modifier.height(20.dp))
            Text(Messages.soonBody(t).substringAfter('\n'), style = Type.body.copy(color = Ink.text))
        }
        Column {
            PrimaryButton("I'm getting ready") { close() }
            Spacer(Modifier.height(14.dp))
            val d = date ?: return@Column
            MovesLine(d)
            Spacer(Modifier.height(8.dp))
            val can = Store.movesLeft(d) > 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("+15m", Modifier.weight(1f), enabled = can) { move(Store.push(this@AlarmActivity, d, t.id, 15), "Pushed 15 minutes.") }
                GhostButton("+30m", Modifier.weight(1f), enabled = can) { move(Store.push(this@AlarmActivity, d, t.id, 30), "Pushed 30 minutes.") }
                GhostButton("Tomorrow", Modifier.weight(1.3f), enabled = can) { move(Store.postponeToTomorrow(this@AlarmActivity, d, t.id), "Moved to tomorrow's plan.") }
            }
        }
    }

    @Composable
    private fun EndView(t: Task) = Shell("Time's up") {
        Column {
            TaskHead(t)
            Spacer(Modifier.height(20.dp))
            Text("How did it go? Phone stats for this block get added when you answer.", style = Type.body)
        }
        Column {
            PrimaryButton("Done") { checkOut(Outcome.DONE) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Partly", Modifier.weight(1f)) { checkOut(Outcome.PARTIAL) }
                GhostButton("Didn't do it", Modifier.weight(1f), color = Ink.bad) { checkOut(Outcome.MISSED) }
            }
            val d = date ?: return@Column
            Spacer(Modifier.height(14.dp))
            MovesLine(d)
            Spacer(Modifier.height(8.dp))
            val can = Store.movesLeft(d) > 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("15 more min", Modifier.weight(1f), enabled = can) { move(Store.extend(this@AlarmActivity, d, t.id, 15), "Extended by 15 minutes.") }
                GhostButton("30 more min", Modifier.weight(1f), enabled = can) { move(Store.extend(this@AlarmActivity, d, t.id, 30), "Extended by 30 minutes.") }
            }
        }
    }

    private fun move(r: Store.MoveResult, ok: String) {
        silence()
        note = Messages.moveOutcome(r, ok)
    }

    @Composable
    private fun NoteView(text: String) = Shell("Plan updated") {
        Column {
            Text(text, style = Type.h1)
            Spacer(Modifier.height(10.dp))
            if (kind == Kind.TASK_SOON || kind == Kind.TASK_END) date?.let { MovesLine(it) }
        }
        PrimaryButton("OK") { finish() }
    }

    private fun checkOut(o: Outcome) {
        val d = date ?: return
        val id = taskId ?: return
        silence()
        result = Store.checkOut(this, d, id, o, early = false)
        if (result == null) finish()
    }

    @Composable
    private fun PlanView(k: Kind) = Shell(if (k == Kind.PLAN_REMIND) "Planning opens at 12:00" else "Last call") {
        Column {
            val streak = Store.streak()
            PixelSprite(Sprites.badgeEarly, Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(if (k == Kind.PLAN_REMIND) "Plan tomorrow" else "Lock in today's plan", style = Type.hero)
            Spacer(Modifier.height(16.dp))
            Text(
                if (k == Kind.PLAN_REMIND) Messages.remindBody(streak) else Messages.lastCallBody(streak),
                style = Type.body.copy(color = Ink.text),
            )
            if (streak > 0) {
                Spacer(Modifier.height(24.dp))
                Text("$streak", style = Type.bigNum.copy(color = Ink.amber))
                Text("day streak", style = Type.small)
            }
        }
        Column {
            PrimaryButton("Open the plan") {
                silence()
                dismissKeyguardThen(this@AlarmActivity) {
                    startActivity(
                        Intent(this@AlarmActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TAB, "plan")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    finish()
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Later", Modifier.fillMaxWidth()) { close() }
        }
    }

    @Composable
    private fun TestView() = Shell("Test alert") {
        Column {
            PixelSprite(Sprites.tabToday, Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Alerts work", style = Type.hero)
            Spacer(Modifier.height(16.dp))
            Text(
                "Task alerts look like this: over the lock screen and other apps, ringing until you press a button. If this only came as a normal notification, allow \"Display over other apps\" and full-screen alerts in Settings.",
                style = Type.body.copy(color = Ink.text),
            )
        }
        PrimaryButton("Looks good") { close() }
    }

    @Composable
    private fun WakeView() = Shell("Wake up") {
        val d = date ?: Clock.todayKey()
        val p = Store.state.plan(d)
        val left = MAX_SNOOZES - p.snoozes
        var now by remember { mutableStateOf(java.time.LocalTime.now()) }
        LaunchedEffect(Unit) { while (true) { delay(10_000); now = java.time.LocalTime.now() } }
        Column {
            PixelSprite(Sprites.sunRise, Modifier.size(width = 128.dp, height = 96.dp))
            Spacer(Modifier.height(16.dp))
            Text(Messages.time(now.hour * 60 + now.minute), style = Type.bigNum.copy(fontSize = 72.sp, lineHeight = 74.sp, color = Ink.amber))
            Text("Good morning", style = Type.hero)
            Spacer(Modifier.height(12.dp))
            Text(Messages.wakeBody(Store.sleepingMinutes()), style = Type.body.copy(color = Ink.text))
        }
        Column {
            PrimaryButton("I'm awake") {
                silence()
                morning = Store.wake(this@AlarmActivity) ?: -1
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(MAX_SNOOZES) { i -> Block(if (i < left) Ink.sky else Ink.line, 10.dp); Spacer(Modifier.width(4.dp)) }
                Spacer(Modifier.width(6.dp))
                Text(if (left > 0) "$left snooze${if (left == 1) "" else "s"} left" else "No snoozes left. Up you get.", style = Type.small)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Snooze $SNOOZE_MINUTES min", Modifier.fillMaxWidth(), color = Ink.sky, enabled = left > 0) {
                Store.snooze(this@AlarmActivity, d)
                finish()
            }
        }
    }

    @Composable
    private fun MorningView(slept: Int) = Shell("Good morning") {
        val today = Store.state.plan(Clock.todayKey())
        val first = today.sortedTasks.firstOrNull { it.result == null && it.endMin > Clock.minutesNow() }
        Column {
            PixelSprite(Sprites.sunRise, Modifier.size(width = 96.dp, height = 72.dp))
            Spacer(Modifier.height(16.dp))
            if (slept >= 0) {
                Label("You slept", color = Ink.sky)
                Text(Messages.duration(slept), style = Type.bigNum.copy(fontSize = 64.sp, lineHeight = 66.sp))
                Text(Messages.sleepVerdict(slept), style = Type.body.copy(color = Ink.text))
            } else {
                Text("Wake-up logged", style = Type.hero)
                Text("Press Sleep before bed tonight and the app will count your hours.", style = Type.body)
            }
            Spacer(Modifier.height(10.dp))
            Text(Messages.morningLine(today.snoozes), style = Type.small)
            if (first != null) {
                Spacer(Modifier.height(20.dp))
                Label("First up")
                Text("${first.title} · ${Messages.time(first.startMin)}", style = Type.title)
            }
        }
        PrimaryButton("Start the day") { finish() }
    }

    @Composable
    private fun BedView() = Shell("Bedtime") {
        val d = date ?: Store.nextWakeDate()
        Column {
            PixelSprite(Sprites.bed, Modifier.size(width = 112.dp, height = 72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Time to sleep", style = Type.hero)
            Spacer(Modifier.height(12.dp))
            Text(Messages.bedtimeBody(Store.state.plan(d)), style = Type.body.copy(color = Ink.text))
        }
        Column {
            PrimaryButton("Going to sleep", color = Ink.sky, shadow = Ink.sky.copy(alpha = 0.45f)) {
                silence()
                Store.startSleep(this@AlarmActivity)
                finish()
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Not yet", Modifier.fillMaxWidth()) { close() }
        }
    }

    @Composable
    private fun MealView() {
        val meal = runCatching { Meal.valueOf(taskId ?: "") }.getOrNull() ?: Meal.LUNCH
        val d = date ?: Clock.todayKey()
        Shell("${meal.label} check") {
            Column {
                PixelSprite(mealSprite(meal), Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text("Had ${meal.label.lowercase()}?", style = Type.hero)
                Spacer(Modifier.height(12.dp))
                Text(Messages.mealBody(meal), style = Type.body.copy(color = Ink.text))
            }
            Column {
                PrimaryButton("Yes, I ate", color = Ink.good, shadow = Ink.good.copy(alpha = 0.45f)) {
                    silence()
                    Store.logMeal(this@AlarmActivity, d, meal, true)
                    note = Messages.mealLogged(meal, true)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Skipped it", Modifier.weight(1f), color = Ink.bad) {
                        silence()
                        Store.logMeal(this@AlarmActivity, d, meal, false)
                        note = Messages.mealLogged(meal, false)
                    }
                    GhostButton("Ask in 30 min", Modifier.weight(1f)) {
                        silence()
                        Store.remindMealLater(this@AlarmActivity, d, meal)
                        finish()
                    }
                }
            }
        }
    }
}
