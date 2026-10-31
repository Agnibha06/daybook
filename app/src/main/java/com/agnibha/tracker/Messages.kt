package com.agnibha.tracker

import java.time.LocalDate

/** All the words the app says. Rotates by date so it doesn't repeat the same line every night. */
object Messages {
    private fun <T> pick(list: List<T>, salt: Int = 0): T =
        list[Math.floorMod(LocalDate.now().dayOfYear * 31 + salt, list.size)]

    fun time(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ap = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12 $ap" else "$h12:${m.toString().padStart(2, '0')} $ap"
    }

    fun duration(min: Int): String = when {
        min < 60 -> "${min}m"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    fun remindBody(streak: Int): String = if (streak > 0)
        "Day $streak is safe. Draft tomorrow now and lock it in after midnight to make it day ${streak + 1}."
    else pick(
        listOf(
            "Draft tomorrow now. You can lock it in from 12:00 to 3:00 AM.",
            "Ten minutes to midnight. Decide tomorrow before tomorrow decides for you.",
            "Planning window opens at 12. Have the first three tasks ready.",
        ),
    )

    fun lastCallBody(streak: Int): String = if (streak > 0)
        "Lock in today's plan before 3:00 AM or your $streak-day streak resets to zero."
    else "Lock in today's plan before 3:00 AM to start a new streak."

    fun brokenBody(previous: Int): String =
        "No plan by 3:00 AM, so the $previous-day streak is over. Your best is still on the map. Tonight starts day 1."

    fun milestoneBody(streak: Int): String = pick(
        listOf(
            "$streak days of planning in a row. That's ${streak / 7} full week${if (streak >= 14) "s" else ""} of keeping your word to yourself.",
            "$streak straight nights. Most people quit in week one. You're past camp ${streak / 7}.",
            "$streak days running. The habit is starting to run you, which is the point.",
        ),
    )

    fun soonBody(t: Task): String {
        val base = "${Messages.time(t.startMin)} to ${Messages.time(t.endMin)} · ${t.category.label}"
        val tip = when (t.category) {
            Category.STUDY, Category.PROJECT -> pick(listOf("Put the phone face down, out of reach.", "Open the notes now so you start on time.", "Water bottle, notes, phone away."), t.startMin)
            Category.CLASS -> pick(listOf("Leave now if you're walking from the hostel.", "Grab your notebook. Attendance counts."), t.startMin)
            Category.LAB -> "Lab coat, manual, and the readings from last time."
            Category.WORKOUT -> "Shoes on. The first five minutes are the hardest part."
            Category.MUSIC -> "Warm up properly before you go full volume."
            Category.ERRAND -> "Get it done and get back to the plan."
            Category.BREAK -> "Wrap up what you're doing. A real break starts soon."
        }
        return "$base\n$tip"
    }

    fun endBody(t: Task): String = "Planned ${duration(t.durationMin)}. Tap to check out and see how focused it was."

    fun resultLine(r: TaskResult, cat: Category): String = when {
        r.outcome == Outcome.MISSED -> pick(listOf("Logged as missed. Honest logs make better plans.", "Missed. Move it to tomorrow's plan if it still matters."), r.efficiency)
        r.unlocks == null -> "Logged. Turn on usage access in Settings to see your phone stats here."
        r.efficiency >= 90 -> pick(listOf("Locked in. Barely touched the phone.", "That's what a real focus block looks like.", "Clean block. Do that again tomorrow."), r.efficiency)
        r.efficiency >= 70 -> pick(listOf("Solid. A couple of drifts, nothing serious.", "Good block. Trim the unlocks and it's a 90."), r.efficiency)
        r.efficiency >= 45 -> if (cat.isFocus) "The phone took a real share of this one." else "Done, with some phone time mixed in."
        else -> if ((r.distractMin ?: 0) > 0) "More scrolling than ${cat.label.lowercase()} this time. Next block, phone in another room." else "Rough one. Shorter blocks might work better."
    }

    fun flashLine(streak: Int): String = pick(
        listOf(
            "Day $streak. Keep climbing.",
            "$streak nights in a row. Good.",
            "Day $streak. The plan is the promise.",
            "Day $streak on the board.",
        ),
        streak,
    )

    fun greeting(hour: Int): String = when (hour) {
        in 0..2 -> "Planning hours"
        in 3..4 -> "Up late"
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
}
