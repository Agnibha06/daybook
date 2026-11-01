# Daybook

A personal Android discipline tracker. Plan the day between 12:00 and 3:00 AM, get alarm-style alerts around every task, and see how focused you actually were.

## What it does

- **Nightly check-in.** A full-screen reminder at 11:50 PM. The plan can only be locked in between 12:00 and 3:00 AM; a plan made then is for the date that just started. Miss 3:00 AM and the streak resets. A last-call alert rings at 2:30 AM if you haven't locked in.
- **Task alerts.** Each task rings 10 minutes before it starts and again when it ends. Alerts open over the lock screen and over other apps, keep ringing, and the back button does nothing until you press a choice.
- **Check-out and efficiency.** At the end of a task you pick Done, Partly or Didn't do it. The app then reads Android's usage history for that time window: how many times you unlocked, minutes on distracting apps, and screen-on time. These combine into an efficiency score, shown on a completion screen.
- **Streak map.** The Climb tab is a winding trail. Your avatar moves up one step per day; every 7th step is a camp. A short animation plays on your first open each day, and a pop-up with falling confetti appears on camp days.
- **Insights.** Completion rate, average efficiency, focus time, minutes between unlocks, a two-week chart, efficiency by hour of day, time per task type, the apps that cost you the most focus, and written patterns once there is enough data.
- **Points and levels.** Points for locking in (more for longer streaks) and for each task you finish, with a bonus for efficiency.
- **Two moves a day.** Once a day is live, you can push a task (+15m, +30m, +1h), pick a new time, extend a running task, or send it to tomorrow, twice per day. Live tasks can't be deleted.
- **Focus mode.** A full-screen timer with a pixel candle that burns down, plus live unlock and distraction counts.
- **Routines.** Save any day's plan as that weekday's routine and load it in one tap.
- **Log.** A month calendar coloured by how much you finished, with each day's tasks, moves, mood and note.
- **Badges.** Twelve pixel badges for streaks, zero-unlock blocks, deep-work days and more.
- **Backup.** Save and restore everything as a JSON file from Settings.

## Run it on your phone

1. On the phone: Settings → About phone → tap **Build number** 7 times. Then Settings → Developer options → turn on **USB debugging**.
2. Open this folder in Android Studio (File → Open). Let Gradle sync finish the first time; it downloads what it needs.
3. Plug in the phone, accept the USB debugging prompt on it, choose your phone at the top of Android Studio, and press **Run** (the green play button).
4. In the app, enter your name, then switch on each permission in the list. On Xiaomi, Realme, Vivo or Oppo phones also turn on **Autostart** for Daybook in the phone's own settings, or alerts may not fire.
5. Settings (gear icon on Today) → **Send test alert**, then lock the phone. You should see the alert take over within 10 seconds.

## How efficiency is scored

Start from what you finished: Done = 100%, Partly = 60%, Missed = 0. For study, class, lab and project blocks, every minute on a distracting app costs its share of the block, and each unlock beyond one per 45 minutes costs 4 points. Workouts and music are penalised half as much. Breaks never are. You choose which apps count as distracting in Settings.

## Project layout

```
app/src/main/java/com/agnibha/tracker/
  Model.kt        tasks, plans, saved state (JSON on the phone)
  Store.kt        planning window, streak, lock-in, check-out
  Alarms.kt       exact alarms, full-screen notifications, boot handling
  AlarmActivity   the full-screen alert screens
  Usage.kt        unlock counts and app time from Android usage stats, scoring
  Insights.kt     numbers and written patterns for the Insights tab
  Messages.kt     everything the app says
  ui/             Today, Plan, Climb, Insights, Setup and Settings screens
```

All data stays on the phone.

Pixel art lives in `sprites/sprites.py`; running it regenerates `ui/Sprites.kt` and the app icons.

Fonts: IBM Plex Sans and Pixelify Sans, under the SIL Open Font License.
