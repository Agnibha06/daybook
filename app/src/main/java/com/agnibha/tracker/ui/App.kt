package com.agnibha.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store
import com.agnibha.tracker.Task
import com.agnibha.tracker.TaskResult
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Tab(val label: String, val glyph: Glyph) {
    TODAY("Today", Glyph.TODAY),
    PLAN("Plan", Glyph.PLAN),
    CLIMB("Climb", Glyph.CLIMB),
    INSIGHTS("Insights", Glyph.INSIGHTS),
}

@Composable
fun App(requestedTab: String?, consumeTab: () -> Unit) {
    val ctx = LocalContext.current
    val s = Store.state
    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        if (!s.setupDone) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { SetupScreen() }
        } else {
            MainShell(requestedTab, consumeTab)
        }
    }
}

@Composable
private fun MainShell(requestedTab: String?, consumeTab: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
        var settings by remember { mutableStateOf(false) }
        var shownResult by remember { mutableStateOf<Pair<Task, TaskResult>?>(null) }
        var flash by remember { mutableIntStateOf(0) }
        var milestone by remember { mutableIntStateOf(0) }

        LaunchedEffect(requestedTab) {
            if (requestedTab == "plan") {
                tab = Tab.PLAN
                consumeTab()
            }
        }

        // Once per day on opening: the streak flash, or the weekly pop-up on a camp day.
        LaunchedEffect(Store.resumeTick) {
            val st = Store.streak()
            val today = Clock.todayKey()
            val lockedToday = Store.state.plan(today).isLocked
            if (st > 0 && st % 7 == 0 && lockedToday && Store.state.lastMilestoneShown != st) {
                milestone = st
                Store.markMilestoneShown(st)
                Store.markFlashShown()
            } else if (st >= 2 && Store.state.lastFlashDate != today) {
                flash = st
                Store.markFlashShown()
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.TODAY -> TodayScreen(
                            onGoToPlan = { tab = Tab.PLAN },
                            onOpenSettings = { settings = true },
                            onCheckOut = { task, outcome, early ->
                                val r = Store.checkOut(ctx, Clock.todayKey(), task.id, outcome, early)
                                if (r != null) shownResult = Store.findTask(Clock.todayKey(), task.id)?.let { it to r }
                            },
                        )
                        Tab.PLAN -> PlanScreen()
                        Tab.CLIMB -> ClimbScreen()
                        Tab.INSIGHTS -> InsightsScreen()
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }

        if (settings) {
            BackHandler { settings = false }
            Box(Modifier.fillMaxSize().background(Ink.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                SettingsScreen { settings = false }
            }
        }

        shownResult?.let { (task, result) ->
            BackHandler { shownResult = null }
            Box(
                Modifier.fillMaxSize().background(Ink.bg).windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                ResultView(task, result) { shownResult = null }
            }
        }

        if (flash > 0) StreakFlash(flash) { flash = 0 }
        if (milestone > 0) Milestone(milestone) { milestone = 0 }
    }
}

@Composable
private fun BottomBar(current: Tab, onPick: (Tab) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
        Row(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TabGlyph(t.glyph, if (on) Ink.amber else Ink.faint)
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = Type.label.copy(color = if (on) Ink.amber else Ink.faint, letterSpacing = Type.label.letterSpacing / 2))
                }
            }
        }
    }
}

/** Shown on the first open of each day once the streak is past day 1. */
@Composable
private fun StreakFlash(streak: Int, onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val count = remember { Animatable((streak - 1).toFloat()) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(220))
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(380)
        count.animateTo(streak.toFloat(), tween(700, easing = FastOutSlowInEasing))
        delay(1700)
        fade.animateTo(0f, tween(260))
        onDone()
    }
    BackHandler { onDone() }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Ink.bg.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PeakGlyph(84.dp, Ink.amber, Modifier.scale(scale.value))
            Spacer(Modifier.height(24.dp))
            Text("${count.value.toInt()}", style = Type.bigNum.copy(fontSize = Type.bigNum.fontSize * 1.6f, lineHeight = Type.bigNum.lineHeight * 1.6f, color = Ink.amber))
            Text("day streak", style = Type.title.copy(color = Ink.dim))
            Spacer(Modifier.height(18.dp))
            Text(Messages.flashLine(streak), style = Type.h2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            Text("Tap to continue", style = Type.small.copy(color = Ink.faint))
        }
    }
}

private data class Bit(val x: Float, val speed: Float, val drift: Float, val size: Float, val spin: Float, val color: Color)

/** Weekly pop-up, with paper scraps falling. */
@Composable
private fun Milestone(streak: Int, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200)) }
    val bits = remember {
        val palette = listOf(Ink.amber, Ink.text, Ink.good, Color(0xFF7FA7E8), Color(0xFFD58FC0))
        List(70) {
            Bit(
                x = Random.nextFloat(),
                speed = 0.6f + Random.nextFloat() * 0.9f,
                drift = (Random.nextFloat() - 0.5f) * 0.25f,
                size = 4f + Random.nextFloat() * 7f,
                spin = Random.nextFloat() * 720f,
                color = palette[it % palette.size],
            )
        }
    }
    BackHandler { onDone() }
    Box(Modifier.fillMaxSize().background(Ink.bg.copy(alpha = 0.97f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            bits.forEach { b ->
                val p = t.value * b.speed
                val y = -40f + p * (size.height + 80f)
                if (y < size.height + 20f) {
                    val x = (b.x + b.drift * p) * size.width
                    rotate(b.spin * t.value, Offset(x, y)) {
                        drawRect(b.color.copy(alpha = (1f - t.value * 0.5f)), Offset(x - b.size, y - b.size / 2), androidx.compose.ui.geometry.Size(b.size * 2, b.size))
                    }
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 28.dp).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("Camp ${streak / 7} reached", color = Ink.amber)
            Spacer(Modifier.height(12.dp))
            Text("Week ${streak / 7} complete", style = Type.hero, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text("$streak", style = Type.bigNum.copy(fontSize = Type.bigNum.fontSize * 1.6f, lineHeight = Type.bigNum.lineHeight * 1.6f, color = Ink.amber))
            Text("days in a row", style = Type.title.copy(color = Ink.dim))
            Spacer(Modifier.height(20.dp))
            Text(Messages.milestoneBody(streak), style = Type.body.copy(color = Ink.text), textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Keep climbing", onClick = onDone)
        }
    }
}
