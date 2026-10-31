package com.agnibha.tracker

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agnibha.tracker.ui.DaybookTheme
import com.agnibha.tracker.ui.GhostButton
import com.agnibha.tracker.ui.Ink
import com.agnibha.tracker.ui.Label
import com.agnibha.tracker.ui.PeakGlyph
import com.agnibha.tracker.ui.PrimaryButton
import com.agnibha.tracker.ui.ResultView
import com.agnibha.tracker.ui.Type
import com.agnibha.tracker.ui.tint

/**
 * The full-screen alert. It shows over the lock screen and other apps, and the
 * back button does nothing: a button has to be pressed to make it go away.
 */
class AlarmActivity : ComponentActivity() {
    private var kind by mutableStateOf<Kind?>(null)
    private var date by mutableStateOf<String?>(null)
    private var taskId by mutableStateOf<String?>(null)
    private var result by mutableStateOf<TaskResult?>(null)

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
                    when {
                        r != null && t != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                            ResultView(t, r) { finish() }
                        }
                        k == Kind.TASK_SOON && t != null -> SoonView(t)
                        k == Kind.TASK_END && t != null -> EndView(t)
                        k == Kind.PLAN_REMIND || k == Kind.PLAN_LAST_CALL -> PlanView(k)
                        k == Kind.TEST -> TestView()
                        else -> LaunchedEffect(Unit) { silence(); finish() }
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeakGlyph(18.dp, Ink.amber)
                Spacer(Modifier.width(10.dp))
                Label(top, color = Ink.amber)
            }
            content()
        }
    }

    @Composable
    private fun SoonView(t: Task) = Shell("Starts in ${Alarms.SOON_MINUTES} minutes") {
        Column {
            Label(t.category.label, color = t.category.tint())
            Spacer(Modifier.height(8.dp))
            Text(t.title, style = Type.hero)
            Spacer(Modifier.height(8.dp))
            Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)} · ${Messages.duration(t.durationMin)}", style = Type.num.copy(color = Ink.dim))
            Spacer(Modifier.height(24.dp))
            Text(Messages.soonBody(t).substringAfter('\n'), style = Type.body.copy(color = Ink.text))
        }
        PrimaryButton("I'm getting ready") { close() }
    }

    @Composable
    private fun EndView(t: Task) = Shell("Time's up") {
        Column {
            Label(t.category.label, color = t.category.tint())
            Spacer(Modifier.height(8.dp))
            Text(t.title, style = Type.hero)
            Spacer(Modifier.height(8.dp))
            Text("${Messages.time(t.startMin)} – ${Messages.time(t.endMin)}", style = Type.num.copy(color = Ink.dim))
            Spacer(Modifier.height(24.dp))
            Text("How did it go? Your phone stats for this block get added when you answer.", style = Type.body)
        }
        Column {
            PrimaryButton("Done") { checkOut(Outcome.DONE) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                GhostButton("Partly", Modifier.weight(1f)) { checkOut(Outcome.PARTIAL) }
                Spacer(Modifier.width(10.dp))
                GhostButton("Didn't do it", Modifier.weight(1f), color = Ink.bad) { checkOut(Outcome.MISSED) }
            }
        }
    }

    @Composable
    private fun TestView() = Shell("Test alert") {
        Column {
            Text("Alerts work", style = Type.hero)
            Spacer(Modifier.height(16.dp))
            Text(
                "Task alerts look like this: over the lock screen and other apps, ringing until you press a button. If this only came as a normal notification, allow \"Display over other apps\" and full-screen alerts in Settings.",
                style = Type.body.copy(color = Ink.text),
            )
        }
        PrimaryButton("Looks good") { close() }
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
            Text(
                if (k == Kind.PLAN_REMIND) "Plan tomorrow" else "Lock in today's plan",
                style = Type.hero,
            )
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
}
