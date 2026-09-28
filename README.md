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
  The lead-in waits for the opening message to finish before it starts
  counting down, so a long one is never talked over. Leave either blank for
  the defaults — "Get ready" and "Workout complete. Nice work."
- Optional next-exercise call-out (on by default): during rest and transition
  the app speaks what's coming — "Rest. Next up: Burpees" — and shows it in
  large type on screen so the PAX can see it from the ground.
- Run screen: 5-second lead-in, giant countdown, block and round
  counters, overall progress bar, pause/resume, and skip-stage. The screen
  stays awake and flips to white during work stages so you can read it from
  the ground.
- Plays well with music: spoken announcements duck whatever is playing — a
  phone music app, a Bluetooth speaker — for as long as they last, then hand
  the volume back, the same way navigation guidance does. Beeps don't duck;
  they just play over the top.
- Announcements never talk over each other. Everything that speaks — stage
  call-outs, the opening and closing messages, scheduled cues — goes through
  one queue and waits its turn. A fire-and-forget line stuck behind others
  for more than ten seconds is dropped rather than said late, since by then
  it describes a stage that has passed.
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
- Share a workout with another PAX: the overflow menu on a timer sends a
  summary plus a compact `f3timer://` link through the normal share sheet —
  Slack, a text, email. On the other phone, tapping the link (or pasting it
  into Import, the arrow on the home screen) shows what it contains and adds
  it as a timer of its own. Blocks, timings, exercises, and the opening and
  closing messages all come across; ids are regenerated so nothing is
  overwritten, and voice settings stay local since the sender's engine may
  not exist on the recipient's phone.
- Scheduled cues (the alarm icon on the home screen): a wall-clock time and
  what to do at it — sound an alert, speak a line, start a saved workout, or
  any combination. Mostly this is for calling time during a beatdown: 5:10
  "five minutes left", 5:15 "time's up". A cue that lands while a workout is
  running speaks through that run's own voice, so the warning and the stage
  announcements share one duck instead of two engines competing. Cues can
  target one block of a workout instead of the whole thing, repeat on chosen
  weekdays (or fire once), and are booked as real alarm clocks so they
  survive Doze and a reboot.
- Optional spoken commands during a run (the mic button on the run screen,
  off until you turn it on): "start the next block", "next", "pause",
  "resume", "how long", and "end the workout". Recognition runs on-device
  where the phone supports it, so it works without signal, and anything heard
  while the app is talking is ignored so its own call-outs can't trigger a
  skip. Ending the workout needs an unambiguous phrase — a bare "stop" is
  deliberately not a command.
- Custom voice replies (Voice replies, in the home overflow menu): teach the
  app a call and response — ask "who has the best pushup form?" mid-workout
  and it answers "It's Sprocket. Hands down." Matching is forgiving, since a
  recogniser outdoors rarely returns the exact sentence: most of the
  trigger's distinctive words is enough, and a trigger written "pushup" still
  matches a phone that hears "push up".
- Starting lights: the whole screen goes red (READY), then yellow (SET),
  then green (GO!), 1.7 seconds apart, with a beep on each and a spoken "Go"
  — readable from the ground across a parking lot, and the one place colour
  shows up in an otherwise black-and-white app. Trigger it from the green
  traffic-light button on the home screen (no workout needed — handy for
  sprints or a race) or the matching button on the run screen, or say "count
  me down" with the mic on.
- Update check: on opening the home screen the app reads `update.json` from
  the repository and, if it describes a newer build than the one installed,
  shows an "Update available" banner. Tapping it lists what changed and
  offers a Download button that opens the link in a browser. Failures — no
  signal, no published manifest — are silent; there is simply no banner.
- F3 black-and-white branding throughout.

## Publishing a build

`update.json` at the repository root describes the newest build, and the app
reads it from the default branch:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "notes": ["One line per change"],
  "downloadUrl": "https://github.com/dachhack/workout_timer/releases/latest"
}
```

To publish: bump `versionCode` and `versionName` in `app/build.gradle.kts`,
build the APK, put it (or a zip of it) somewhere downloadable — a GitHub
release is the easy option — then set the same version and that link in
`update.json` and push. Phones running an older build show the banner the
next time the home screen opens.

## Building

Requires JDK 17+ and the Android SDK (API 35).

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install` or open the project in Android Studio and hit Run.

Unit tests cover the block/interval sequencing, duration math, and the
share-link round trip:

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
- `data/TimerShare.kt` — encoding a timer into a shareable link and reading
  one back.
- `model/ScheduledCue.kt` + `data/ScheduleRepository.kt` — the clock-time cues
  and their next-occurrence maths.
- `alarm/` — booking cues with AlarmManager, the receivers that catch them
  (including after a reboot), and the service that plays one.
- `model/VoiceCommand.kt` + `voice/VoiceCommands.kt` — the command grammar and
  the speech recogniser that feeds it.
- `model/CustomReply.kt` + `data/BanterRepository.kt` — the custom call-and-
  response lines and the forgiving matcher behind them.
- `data/PaxPhotoStore.kt` — the splash photos: gallery imports plus any
  bundled in `assets/pax/`.
- `ui/` — Compose screens: splash, home (timer list), edit, and run.
