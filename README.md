# F3 Workout Timer

An Android interval timer for F3-style beatdowns. You program the whole
workout as a sequence of **blocks** — a warm-up, a cardio circuit, a weights
circuit, a cool-down, as many as you want. Each block holds its own exercises,
round count, and **Work** / **Rest** / **Transition** timings, and the app
totals the length of the whole thing. During the run every block, exercise,
and stage is announced with text-to-speech over a big on-screen countdown,
with 3-2-1 beeps into each change.

## Features

- Create, edit, and delete named timers; they're saved on the device.
- **A workout is a list of blocks**, run top to bottom, and you program as many
  as you like — e.g. Warm-up → Cardio → Weights → Cool-down. Each block is its
  own circuit with:
  - a name (shown on the run screen and spoken when the block starts),
  - its own exercise list — one line is one timed interval, and every round
    runs the whole list in order, so three lines for four rounds is twelve
    work intervals. Several movements on one line, separated by commas, share
    a single interval: "5 Squats, 5 Merkins, 5 Sit-ups" is one work period
    covering all three, stacked in large type on the run screen,
  - its own round count, and
  - its own work / rest / transition timings, each optional with an optional
    spoken message.
  A block with no exercises is a plain interval block; a one-round block with
  only work enabled is a single timed block (warm-up, cool-down, COT).
  Blocks can be reordered, collapsed, and removed in the editor.
- Live total-workout length for the whole timer and for each block. The
  workout never ends on a dangling rest or transition.
- Voice picker per timer: choose any installed text-to-speech engine (Google,
  Samsung, third-party) and any of its voices, with a spoken preview when you
  select one. Blank keeps the device defaults.
- Optional opening and closing messages per timer: what the app says as the
  run starts ("Circle up, gentlemen") and once the last interval is done.
  Leave either blank for the defaults — "Get ready" and "Workout complete.
  Nice work."
- Optional next-exercise call-out (on by default): during rest and transition
  the app speaks what's coming — "Rest. Next up: Burpees" — and shows it in
  large type on screen so the PAX can see it from the ground.
- Run screen: 5-second "Get ready" lead-in, giant countdown, block and round
  counters, overall progress bar, pause/resume, and skip-stage. The screen
  stays awake and flips to white during work stages so you can read it from
  the ground.
- Plays well with music: spoken announcements duck whatever is playing — a
  phone music app, a Bluetooth speaker — for as long as they last, then hand
  the volume back, the same way navigation guidance does. Beeps don't duck;
  they just play over the top.
- The run lives in a foreground service, so it keeps ticking (and talking)
  with the screen locked or the app backgrounded. The notification shows the
  live countdown with pause/stop actions; backing out of the run screen
  leaves the workout running, and the home screen shows a resume banner.
- A splash screen on open: a rotating bit of F3 encouragement ("You working
  out, bro?", "The fartsack is not your friend.") over a random photo of the
  PAX. Photos bundled in `app/src/main/assets/pax/` ship with the app, and
  each phone can add its own from the gallery with the camera icon on the home
  screen; the two sets are pooled and one is drawn at random each launch.
  Tap to skip; opening the app from the run notification skips it entirely.
- F3 black-and-white branding throughout.

## Building

Requires JDK 17+ and the Android SDK (API 35).

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install` or open the project in Android Studio and hit Run.

Unit tests cover the block/interval sequencing and duration math:

```sh
./gradlew testDebugUnitTest
```

## Structure

- `model/WorkoutTimer.kt` — the timer / block / stage model and the interval
  sequence + total-duration math.
- `data/TimerRepository.kt` — persistence (Preferences DataStore, JSON),
  including migration of timers saved before the block restructure.
- `timer/TimerEngine.kt` — the run loop: ticking clock, pause/skip, beep and
  speech cues, next-exercise call-out.
- `timer/TimerService.kt` — foreground service that owns the run: live
  notification, wake lock, pause/stop actions.
- `audio/WorkoutSounds.kt` — text-to-speech (voice and engine selection),
  tones, and the ducking of other audio while speech plays.
- `data/PaxPhotoStore.kt` — the splash photos: gallery imports plus any
  bundled in `assets/pax/`.
- `ui/` — Compose screens: splash, home (timer list), edit, and run.
