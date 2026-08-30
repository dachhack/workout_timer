# F3 Workout Timer

An Android interval timer for F3-style beatdowns. Build a timer from three
optional stages — **Work**, **Rest**, and **Transition** — set the number of
rounds, and the app totals the workout length. During the run each stage is
announced with text-to-speech (a custom message per stage, or the stage name
by default), a big on-screen countdown, and 3-2-1 beeps at the end of every
stage.

## Features

- Create, edit, and delete named timers; they're saved on the device.
- Three stages per round, each one optional: work, rest, transition.
- Optional warm-up block before round 1 and cool-down block after the final
  round, each with its own duration and spoken message.
- Per-stage spoken message via Android text-to-speech (e.g. "Merkins, go!").
- Per-round exercise list: one exercise per line in the editor; round N gets
  line N (the list repeats if there are more rounds than lines). The exercise
  is shown big on screen and spoken at the start of each work stage, with an
  "up next" cue during rest and transition.
- Voice picker per timer: choose any installed text-to-speech engine (Google,
  Samsung, third-party) and any of its voices, with a spoken preview when you
  select one. Blank keeps the device defaults.
- Optional halfway call-out spoken at the midpoint of the workout.
- Rounds picker with a live total-workout-length readout. Rest and transition
  are skipped after the final round, and the total reflects that.
- Run screen: 5-second "Get ready" lead-in, giant countdown, round counter,
  overall progress bar, pause/resume, and skip-stage. The screen stays awake
  and flips to white during work stages so you can read it from the ground.
- The run lives in a foreground service, so it keeps ticking (and talking)
  with the screen locked or the app backgrounded. The notification shows the
  live countdown with pause/stop actions; backing out of the run screen
  leaves the workout running, and the home screen shows a resume banner.
- F3 black-and-white branding throughout.

## Building

Requires JDK 17+ and the Android SDK (API 35).

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install` or open the project in Android Studio and hit Run.

## Structure

- `model/WorkoutTimer.kt` — timer/stage data model and the interval sequence +
  total-duration math.
- `data/TimerRepository.kt` — persistence (Preferences DataStore, JSON).
- `timer/TimerEngine.kt` — the run loop: ticking clock, pause/skip, beep and
  speech cues, halfway call-out.
- `timer/TimerService.kt` — foreground service that owns the run: live
  notification, wake lock, pause/stop actions.
- `audio/WorkoutSounds.kt` — text-to-speech (with voice selection) and tones.
- `ui/` — Compose screens: home (timer list), edit, and run.
