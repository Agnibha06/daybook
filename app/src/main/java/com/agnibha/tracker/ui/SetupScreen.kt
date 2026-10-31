package com.agnibha.tracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agnibha.tracker.Alarms
import com.agnibha.tracker.Alerts
import com.agnibha.tracker.Store
import com.agnibha.tracker.Usage

data class Perm(
    val title: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean,
    val open: (Context) -> Unit,
)

private fun settingsFor(action: String, ctx: Context, withPackage: Boolean = true): Intent =
    Intent(action).apply {
        if (withPackage) data = Uri.parse("package:${ctx.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private fun launch(ctx: Context, primary: Intent, fallback: Intent) {
    runCatching { ctx.startActivity(primary) }.onFailure { runCatching { ctx.startActivity(fallback) } }
}

fun permissions(ctx: Context, requestNotifications: () -> Unit): List<Perm> {
    val appDetails = settingsFor(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ctx)
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val pm = ctx.getSystemService(PowerManager::class.java)
    return buildList {
        add(Perm("Notifications", "So reminders and task alerts can reach you.", notifOk, true) { requestNotifications() })
        add(
            Perm("Alarms & reminders", "Lets alerts fire at the exact minute, even when the phone is asleep.", Alarms.canExact(ctx), true) { c ->
                if (Build.VERSION.SDK_INT >= 31) launch(c, settingsFor(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, c), appDetails)
            },
        )
        if (Build.VERSION.SDK_INT >= 34) {
            add(
                Perm("Full-screen alerts", "Shows alerts over the lock screen like an alarm clock.", Alerts.canFullScreen(ctx), true) { c ->
                    launch(c, settingsFor(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, c), appDetails)
                },
            )
        }
        add(
            Perm("Display over other apps", "Puts the alert on top of Instagram, YouTube or whatever else is open.", Settings.canDrawOverlays(ctx), true) { c ->
                launch(c, settingsFor(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, c), appDetails)
            },
        )
        add(
            Perm("Usage access", "Counts unlocks and time on distracting apps during each task. Nothing leaves your phone.", Usage.hasAccess(ctx), true) { c ->
                launch(c, settingsFor(Settings.ACTION_USAGE_ACCESS_SETTINGS, c, withPackage = false), appDetails)
            },
        )
        add(
            Perm(
                "Ignore battery optimisation",
                "Stops the phone from killing alerts to save battery. On Xiaomi, Realme, Vivo or Oppo, also turn on Autostart for Daybook.",
                pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true,
                false,
            ) { c ->
                @Suppress("BatteryLife")
                launch(c, settingsFor(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, c), appDetails)
            },
        )
    }
}

@Composable
fun PermissionList() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = Store.resumeTick // re-check when returning from Settings
    var localTick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    val list = remember(tick, localTick) {
        permissions(ctx) {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEach { p -> PermRow(p) }
    }
}

@Composable
private fun PermRow(p: Perm) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.surface)
            .clickable(enabled = !p.granted) { p.open(ctx) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (p.granted) Ink.good.copy(alpha = 0.2f) else Ink.raised),
            contentAlignment = Alignment.Center,
        ) {
            Dot(if (p.granted) Ink.good else if (p.required) Ink.amber else Ink.faint, 8.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = Type.title)
            Text(p.why, style = Type.small)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (p.granted) "On" else "Allow",
            style = Type.small.copy(color = if (p.granted) Ink.good else Ink.amber, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(18)) },
        singleLine = true,
        textStyle = Type.title,
        placeholder = { Text("Your name", style = Type.title.copy(color = Ink.faint)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.amber,
            unfocusedBorderColor = Ink.line,
            cursorColor = Ink.amber,
            focusedContainerColor = Ink.surface,
            unfocusedContainerColor = Ink.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SetupScreen() {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(Store.state.username) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PeakGlyph(20.dp, Ink.amber)
            Spacer(Modifier.width(10.dp))
            Text("Daybook", style = Type.title.copy(fontWeight = FontWeight.SemiBold))
        }
        Spacer(Modifier.height(36.dp))
        if (step == 0) {
            Text("Plan the night before. Keep your word the next day.", style = Type.h1)
            Spacer(Modifier.height(16.dp))
            Text(
                "Every night between 12:00 and 3:00 AM you lock in the day's plan. That's your check-in. Miss 3:00 AM and the streak resets. During the day, each task rings 10 minutes before it starts and again when it ends, and you check out to see how focused you were.",
                style = Type.body,
            )
            Spacer(Modifier.height(32.dp))
            Label("What should the map call you?")
            Spacer(Modifier.height(10.dp))
            NameField(name) { name = it }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Continue", enabled = name.isNotBlank()) {
                Store.update { it.copy(username = name.trim()) }
                step = 1
            }
        } else {
            Text("Give it the power to bother you", style = Type.h1)
            Spacer(Modifier.height(12.dp))
            Text(
                "Android blocks loud, full-screen alerts until you allow them. Tap each one, switch it on, and come back.",
                style = Type.body,
            )
            Spacer(Modifier.height(24.dp))
            PermissionList()
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Start") { Store.update { it.copy(setupDone = true) } }
            Spacer(Modifier.height(10.dp))
            Text("You can change all of this later in Settings.", style = Type.small)
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s = Store.state
    var name by remember { mutableStateOf(s.username) }
    @Suppress("UNUSED_VARIABLE") val tick = Store.resumeTick
    val apps = remember(tick) { Usage.recentApps(ctx) }
    var testSent by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Ink.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = Type.h1, modifier = Modifier.weight(1f))
            GhostButton("Done") {
                if (name.isNotBlank() && name.trim() != s.username) Store.update { it.copy(username = name.trim()) }
                onClose()
            }
        }
        Label("Name on the map")
        NameField(name) { name = it }

        Spacer(Modifier.height(6.dp))
        Label("Permissions")
        PermissionList()
        Panel {
            Text("Test the alerts", style = Type.title)
            Spacer(Modifier.height(4.dp))
            Text("Sends a sample alert in 10 seconds. Lock the phone or open another app to see it take over.", style = Type.small)
            Spacer(Modifier.height(12.dp))
            GhostButton(if (testSent) "Sent. Wait 10 seconds" else "Send test alert", Modifier.fillMaxWidth(), color = Ink.amber) {
                Alarms.scheduleTest(ctx)
                testSent = true
            }
        }

        Spacer(Modifier.height(6.dp))
        Label("Distracting apps")
        Text("Time on these during a task lowers its efficiency. Switch on anything that pulls you away.", style = Type.small)
        if (apps.isEmpty()) {
            Text("Allow usage access to see the apps you use most.", style = Type.small.copy(color = Ink.amber))
        }
        apps.forEach { a ->
            val on = a.pkg in s.distracting
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.surface).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = Type.title)
                    Text("${a.minutes / 60}h ${a.minutes % 60}m this week", style = Type.small)
                }
                Switch(
                    checked = on,
                    onCheckedChange = { v ->
                        Store.update { st -> st.copy(distracting = if (v) st.distracting + a.pkg else st.distracting - a.pkg) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.bg,
                        checkedTrackColor = Ink.amber,
                        uncheckedThumbColor = Ink.dim,
                        uncheckedTrackColor = Ink.raised,
                        uncheckedBorderColor = Ink.line,
                    ),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Label("How efficiency is scored")
        Panel {
            Text(
                "Start from what you finished: Done counts 100%, Partly 60%, Missed 0. Then for study, class, lab and project blocks, every minute on a distracting app costs its share of the block, and each unlock beyond one per 45 minutes costs 4 points. Workouts and music are penalised half as much. Breaks never are.",
                style = Type.small,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
