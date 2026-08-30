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
- Per-stage spoken message via Android text-to-speech (e.g. "Merkins, go!").
- Rounds picker with a live total-workout-length readout. Rest and transition
  are skipped after the final round, and the total reflects that.
- Run screen: 5-second "Get ready" lead-in, giant countdown, round counter,
  overall progress bar, pause/resume, and skip-stage. The screen stays awake
  and flips to white during work stages so you can read it from the ground.
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
  speech cues.
- `audio/WorkoutSounds.kt` — text-to-speech and tone playback.
- `ui/` — Compose screens: home (timer list), edit, and run.
