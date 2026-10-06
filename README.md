# My Fitness App

An Android app for tracking running, cycling, and weightlifting workouts.
Written in Kotlin with XML Views + ViewBinding (no Jetpack Compose),
MVVM, manual DI through `AppContainer`, coroutines and `StateFlow`.
Talks to a plain PHP + MySQL backend.

Target: an NCC-level L5 individual assignment. The written report
accompanies this repository.

## Features

- **Dashboard** — current week's momentum, hero card with sessions /
  active minutes / distance / kcal, lime streak pill, Material 3 week
  strip with lime dots on active days, personalised insight card.
- **History** — section-headered list of past workouts with weekly
  momentum chart. Tap a row to open the detail screen (map, stats,
  per-kilometre splits for GPS workouts).
- **Live GPS tracking** — `FusedLocationProviderClient` behind a
  foreground service; three-filter noise suppression (accuracy, min
  step, teleport); osmdroid map with a lime polyline; pause / resume /
  stop with on-screen save. See `docs/dev-log.md` phase 8a for the
  design decisions.
- **Goals** — distance / count / duration / volume targets scoped to
  this week or this month; three-section layout (Active / Completed /
  Ended) with a featured card for the most urgent goal.
- **Progress + analytics** — period selector (Week / Month / Year),
  activity chips, period bar chart, trend chart (pace / speed /
  volume), per-activity statistics cards, lifetime personal records.
- **Profile** — avatar upload with Android photo picker, editable
  name / weight / height, BMI with category, three summary tiles,
  password-gated account deletion.

## Required components (assignment brief)

- **RecyclerView** — `fragment_workout_history.xml`, `fragment_goals.xml`, `fragment_log_strength_workout.xml`.
- **FloatingActionButton** — History FAB + Goals mini-FAB.
- **Geolocation** — `app/src/main/java/com/elijah/fitness/tracking/`
  (engine, service, filter constants) + `ui/tracking/LiveTrackingFragment`.

The three inheritance hierarchies the written report explains:

1. `Workout` → `DistanceWorkout` → `RunningWorkout` / `CyclingWorkout` (+ `StrengthWorkout`).
2. `BaseFragment<VB : ViewBinding>` — every fragment extends it.
3. `BaseViewHolder<T>` → the two workout view holders.

Plus a fourth, phase-7 addition:
4. `Goal` → `DistanceGoal` / `WorkoutCountGoal` / `DurationGoal` / `StrengthVolumeGoal`.

See `docs/assignment-coverage.md` for a full requirement → implementation
map.

## Getting it running

### 1. Backend (any one of the two routes)

- **Docker (dev convenience)**: `cd backend && docker compose up -d`.
  API ends up at <http://localhost:8080>; phpMyAdmin at <http://localhost:8081>.
- **XAMPP (what the examiner is likely to use)**: see the "Option B"
  section of [`backend/README.md`](backend/README.md). Copy the
  `fitness-api/` folder into XAMPP's `htdocs`, import the five SQL
  files in order via phpMyAdmin.

### 2. Android app

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleLocalDebug
./gradlew installLocalDebug          # requires a running emulator / device
```

The project is Android Studio-friendly: open the repository root, let
Gradle sync, and press **Run**. There are two build flavors on the `env`
dimension:

- `local` — `BASE_URL = http://10.0.2.2:8080/` (Docker on the host).
  Edit the string in `app/build.gradle.kts` if your backend lives elsewhere.
- `cloud` — placeholder HTTPS URL; set this if you deploy the backend
  somewhere other than the dev machine.

### 3. Test credentials

Seeded user:

- Email: `alex@example.com`
- Password: `Password123`

The seed includes nine workouts across all three activity types spread
across the last 14 days, three in-progress goals, and a profile with
weight (70 kg) + height (178 cm) set — so BMI, kcal, and the insight
card are populated on first open.

Register a fresh account from the auth flow if you prefer; the
assignment-coverage requirements are the same.

## Project layout

```
.
├── app/                   Android module (Kotlin, XML Views, ViewBinding)
│   ├── src/main/java/com/elijah/fitness/
│   │   ├── FitnessApp.kt          Application + AppContainer owner
│   │   ├── AppContainer.kt        Manual DI container
│   │   ├── data/model/            Domain models (Workout, Goal, …)
│   │   ├── data/remote/           Retrofit + DTOs + mappers
│   │   ├── data/repository/       WorkoutRepository, GoalRepository, …
│   │   ├── tracking/              Phase 8a — location engine, foreground service, GPX utilities
│   │   ├── analytics/             Phase 8 — pure-Kotlin Progress calculations
│   │   ├── insights/              Phase 8 — personalised-insights rule engine
│   │   └── ui/                    Fragments + view-models, one package per screen
│   └── src/test/java/…            233 pure-JVM unit tests (as of phase 8 step 6)
├── backend/               PHP + MySQL API (framework-free)
│   ├── fitness-api/               The actual API (Composer-free)
│   ├── sql/                       Schema + seed + migrations
│   └── api-tests.http             42 end-to-end request cases
├── docs/
│   ├── dev-log.md                 Per-step design journal (phase 1 → phase 8)
│   ├── design/                    Stitch design exports (reference)
│   ├── testing/sample_route.gpx   GPX fixture for the live-tracking demo
│   ├── screenshots/README.md      Report-screenshot capture plan
│   ├── assignment-coverage.md     Brief requirements → implementation map
│   └── prompts/                   The phase briefs this project was built from
└── gradle/libs.versions.toml      Dependency catalogue
```

## Known limitations

- **Offline sync is not implemented.** The repository talks to the API
  directly; a lost network connection surfaces as a Snackbar with
  Retry, and tracked data stays on screen until a save succeeds. A
  future phase would add Room caching and WorkManager background sync.
- **Process-kill mid-GPS-tracking drops the session.** The foreground
  service holds the authoritative `TrackingState` in memory; there's
  no on-disk journal. Noted in `docs/dev-log.md` phase 8a step 4.
- **Email is not editable from the app.** A real email change needs a
  verification flow this project doesn't ship. `PUT /me` silently
  drops any `email` field in the body; the Profile screen shows the
  address with a "Email can't be changed from the app." hint.
- **Phase-7 goals do not auto-recur.** When a weekly goal's period
  ends, the goal moves to the "Ended" section (not back to Active).
  Discussed in the written report.
- **osmdroid map tiles download on first use.** After that they cache
  for offline use; a completely-offline demo requires pre-caching the
  area by zooming in once with network available.

## Reading the dev-log

`docs/dev-log.md` is the design journal. Each step records what was
built, the key design decision, one rejected alternative with its
reason, which Kotlin / Android concepts came up, and which assignment
task(s) the step supports. The written report's architecture chapter
pulls heavily from these entries.

## Building and testing

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleLocalDebug          # debug APK for the 'local' flavor
./gradlew testLocalDebugUnitTest      # all unit tests (JVM, no emulator)
./gradlew installLocalDebug           # install onto a running emulator
```

A clean clone needs a `local.properties` file pointing at the Android
SDK — Android Studio generates this on first open. `local.properties`
is `.gitignore`d.
