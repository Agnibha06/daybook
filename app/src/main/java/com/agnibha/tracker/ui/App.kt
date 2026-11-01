package com.agnibha.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agnibha.tracker.Badge
import com.agnibha.tracker.Badges
import com.agnibha.tracker.Clock
import com.agnibha.tracker.Messages
import com.agnibha.tracker.Store
import com.agnibha.tracker.Task
import com.agnibha.tracker.TaskResult
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.random.Random

enum class Tab(val label: String) {
    TODAY("Today"),
    PLAN("Plan"),
    CLIMB("Climb"),
    INSIGHTS("Stats"),
    LOG("Log");

    val sprite: Sprite
        get() = when (this) {
            TODAY -> Sprites.tabToday
            PLAN -> Sprites.tabPlan
            CLIMB -> Sprites.tabClimb
            INSIGHTS -> Sprites.tabInsights
            LOG -> Sprites.tabLog
        }
}

/** App-wide navigation state, so any screen can open an overlay. */
object Nav {
    var tab by mutableStateOf(Tab.TODAY)
    var settings by mutableStateOf(false)
    var result by mutableStateOf<Pair<Task, TaskResult>?>(null)
    /** date to task id */
    var focus by mutableStateOf<Pair<String, String>?>(null)
    /** date of the day being viewed in the Log */
    var day by mutableStateOf<String?>(null)
    /** Points earned by tonight's lock-in, to celebrate it. */
    var locked by mutableStateOf<Int?>(null)

    fun showResult(date: String, id: String, r: TaskResult) {
        Store.findTask(date, id)?.let { result = it to r }
    }
}

@Composable
fun App(requestedTab: String?, consumeTab: () -> Unit) {
    val s = Store.state
    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        AnimatedContent(
            targetState = s.setupDone,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "setup",
        ) { done ->
            if (!done) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { SetupScreen() }
            } else {
                MainShell(requestedTab, consumeTab)
            }
        }
    }
}

@Composable
private fun MainShell(requestedTab: String?, consumeTab: () -> Unit) {
    var flash by remember { mutableIntStateOf(0) }
    var milestone by remember { mutableIntStateOf(0) }
    var badge by remember { mutableStateOf<Badge?>(null) }

    LaunchedEffect(requestedTab) {
        if (requestedTab == "plan") {
            Nav.tab = Tab.PLAN
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

    // Announce newly earned badges once nothing else is on screen.
    val busy = flash > 0 || milestone > 0 || Nav.result != null || Nav.locked != null || Nav.focus != null
    LaunchedEffect(Store.state, busy) {
        if (busy || badge != null) return@LaunchedEffect
        delay(500)
        val fresh = Badges.fresh(Store.state)
        if (fresh.isNotEmpty()) {
            badge = fresh.first()
            Store.markBadgesSeen(listOf(fresh.first().id))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = Nav.tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { w -> dir * w / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { w -> -dir * w / 6 } + fadeOut(tween(140)))
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.TODAY -> TodayScreen()
                        Tab.PLAN -> PlanScreen()
                        Tab.CLIMB -> ClimbScreen()
                        Tab.INSIGHTS -> InsightsScreen()
                        Tab.LOG -> LogScreen()
                    }
                }
            }
            BottomBar(Nav.tab) { Nav.tab = it }
        }

        Overlay(Nav.settings, onBack = { Nav.settings = false }) {
            SettingsScreen { Nav.settings = false }
        }

        Overlay(Nav.day != null, onBack = { Nav.day = null }) {
            Nav.day?.let { DayDetail(it) { Nav.day = null } }
        }

        Overlay(Nav.focus != null, onBack = { Nav.focus = null }) {
            Nav.focus?.let { (d, id) -> FocusScreen(d, id) { Nav.focus = null } }
        }

        Overlay(Nav.result != null, onBack = { Nav.result = null }) {
            Nav.result?.let { (task, result) ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    ResultView(task, result) { Nav.result = null }
                }
            }
        }

        Nav.locked?.let { pts -> LockCelebration(Store.streak(), pts) { Nav.locked = null } }
        if (flash > 0) StreakFlash(flash) { flash = 0 }
        if (milestone > 0) Milestone(milestone) { milestone = 0 }
        badge?.let { b -> BadgePopup(b) { badge = null } }

        ToastHost(Modifier.align(Alignment.BottomCenter))
    }
}

/** Full-screen layer that slides up from the bottom and covers the tab bar. */
@Composable
private fun Overlay(visible: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180)),
    ) {
        BackHandler(onBack = onBack)
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) { content() }
    }
}

@Composable
private fun ToastHost(modifier: Modifier) {
    val msg = Toaster.message
    LaunchedEffect(Toaster.serial) {
        if (msg != null) {
            delay(2600)
            Toaster.clear()
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = slideInVertically(tween(220)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 84.dp, start = 20.dp, end = 20.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().background(Ink.raised, PxShape).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(msg ?: "", style = Type.small.copy(color = Ink.text))
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onPick: (Tab) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().height(2.dp)) {
            Tab.entries.forEach { t ->
                Box(Modifier.weight(1f).height(2.dp).background(if (t == current) Ink.amber else Ink.line))
            }
        }
        Row(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                val lift by animateFloatAsState(
                    if (on) 1f else 0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "lift",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PixelSprite(
                        t.sprite,
                        Modifier.size(26.dp).graphicsLayer {
                            translationY = -4.dp.toPx() * lift
                            scaleX = 1f + 0.12f * lift
                            scaleY = 1f + 0.12f * lift
                        },
                        muted = !on,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = Type.label.copy(color = if (on) Ink.amber else Ink.faint, letterSpacing = 0.5.sp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Moments

/** Squares that fill the screen in a diagonal sweep, then clear. 0 = empty, 1 = covered. */
@Composable
private fun PixelWipe(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val cell = 28.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val span = (cols + rows).toFloat()
        for (r in 0 until rows) for (c in 0 until cols) {
            val local = ((progress * (span + 6f)) - (c + r)) / 6f
            val f = local.coerceIn(0f, 1f)
            if (f <= 0f) continue
            val s = floor(cell * f)
            drawRect(color, Offset(c * cell + (cell - s) / 2, r * cell + (cell - s) / 2), Size(s, s))
        }
    }
}

@Composable
private fun LockCelebration(streak: Int, points: Int, onDone: () -> Unit) {
    val wipe = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        wipe.animateTo(1f, tween(650, easing = LinearEasing))
        content.animateTo(1f, tween(300))
    }
    LaunchedEffect(Unit) {
        while (true) { delay(260); frame++ }
    }
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        PixelWipe(wipe.value, Ink.surface)
        Column(
            Modifier.alpha(content.value).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelSprite(if (frame % 2 == 0) Sprites.climberA else Sprites.climberB, Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Label("Locked in", color = Ink.amber)
            Spacer(Modifier.height(8.dp))
            Text(if (streak <= 1) "Day 1 on the map" else "Day $streak on the map", style = Type.hero, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("+$points points", style = Type.num.copy(color = Ink.amber, fontSize = 20.sp))
            Spacer(Modifier.height(16.dp))
            Text("Alerts are set. Get some sleep.", style = Type.body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Text("Tap to continue", style = Type.small.copy(color = Ink.faint))
        }
    }
}

/** Shown on the first open of each day once the streak is past day 1. */
@Composable
private fun StreakFlash(streak: Int, onDone: () -> Unit) {
    val wipe = remember { Animatable(0f) }
    val pop = remember { Animatable(0.3f) }
    val count = remember { Animatable((streak - 1).toFloat()) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        wipe.animateTo(1f, tween(450, easing = LinearEasing))
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(500)
        count.animateTo(streak.toFloat(), tween(700, easing = FastOutSlowInEasing))
        delay(1800)
        onDone()
    }
    LaunchedEffect(Unit) { while (true) { delay(240); frame++ } }
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        PixelWipe(wipe.value, Ink.bg)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(wipe.value)) {
            Box(contentAlignment = Alignment.BottomCenter) {
                PixelSprite(Sprites.tabClimb, Modifier.size(132.dp).scale(pop.value))
                PixelSprite(
                    if (frame % 2 == 0) Sprites.climberA else Sprites.climberB,
                    Modifier.size(52.dp).padding(bottom = 0.dp).graphicsLayer { translationX = -46.dp.toPx(); translationY = 4.dp.toPx() },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("${count.value.toInt()}", style = Type.bigNum.copy(fontSize = 84.sp, lineHeight = 86.sp, color = Ink.amber))
            Text("day streak", style = Type.title.copy(color = Ink.dim))
            Spacer(Modifier.height(16.dp))
            Text(Messages.flashLine(streak), style = Type.h2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(36.dp))
            Text("Tap to continue", style = Type.small.copy(color = Ink.faint))
        }
    }
}

private data class Bit(val x: Float, val speed: Float, val drift: Float, val size: Float, val color: Color)

/** Weekly pop-up, with pixel confetti. */
@Composable
private fun Milestone(streak: Int, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    val wipe = remember { Animatable(0f) }
    LaunchedEffect(Unit) { wipe.animateTo(1f, tween(450, easing = LinearEasing)) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3600, easing = LinearEasing)) }
    val bits = remember {
        val palette = listOf(Ink.amber, Ink.text, Ink.good, Ink.sky, Color(0xFFD58FC0), Color(0xFFF7D354))
        List(80) {
            Bit(Random.nextFloat(), 0.55f + Random.nextFloat() * 0.9f, (Random.nextFloat() - 0.5f) * 0.2f, 3f + Random.nextInt(3) * 2f, palette[it % palette.size])
        }
    }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PixelWipe(wipe.value, Ink.bg)
        Canvas(Modifier.fillMaxSize()) {
            val unit = 2.dp.toPx()
            bits.forEach { b ->
                val p = t.value * b.speed
                val y = floor((-40f + p * (size.height + 80f)) / unit) * unit
                if (y < size.height) {
                    val x = floor(((b.x + b.drift * p) * size.width) / unit) * unit
                    val s = b.size * unit
                    drawRect(b.color, Offset(x, y), Size(s, s), alpha = 1f - t.value * 0.4f)
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 28.dp).windowInsetsPadding(WindowInsets.safeDrawing).alpha(wipe.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PixelSprite(Sprites.tent, Modifier.size(112.dp))
            Spacer(Modifier.height(16.dp))
            Label("Camp ${streak / 7} reached", color = Ink.amber)
            Spacer(Modifier.height(8.dp))
            Text("Week ${streak / 7} complete", style = Type.hero, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("$streak", style = Type.bigNum.copy(fontSize = 72.sp, lineHeight = 74.sp, color = Ink.amber))
            Text("days in a row", style = Type.title.copy(color = Ink.dim))
            Spacer(Modifier.height(16.dp))
            Text(Messages.milestoneBody(streak), style = Type.body.copy(color = Ink.text), textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Keep climbing", onClick = onDone)
        }
    }
}

@Composable
private fun BadgePopup(b: Badge, onDone: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC07080B))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .scale(0.6f + 0.4f * pop.value)
                .alpha(pop.value.coerceIn(0f, 1f))
                .background(Ink.surface, PxShape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("Badge earned", color = Ink.amber)
            Spacer(Modifier.height(16.dp))
            PixelSprite(badgeSprite(b.id), Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text(b.name, style = Type.h1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(b.how, style = Type.body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(200.dp)) { PrimaryButton("Nice", onClick = onDone) }
        }
    }
}

fun badgeSprite(id: String): Sprite = when (id) {
    "first" -> Sprites.badgeFirst
    "week" -> Sprites.badgeWeek
    "fortnight" -> Sprites.badgeFortnight
    "month" -> Sprites.badgeMonth
    "early" -> Sprites.badgeEarly
    "ghost" -> Sprites.badgeGhost
    "deep" -> Sprites.badgeDeep
    "sweep" -> Sprites.badgeSweep
    "shield" -> Sprites.badgeShield
    "iron" -> Sprites.badgeIron
    "steady" -> Sprites.badgeSteady
    "coin" -> Sprites.badgeCoin
    else -> Sprites.badgeLock
}
