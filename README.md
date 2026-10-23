# My Fitness App

An Android app for tracking running, cycling, swimming, rowing and
weightlifting ("Workout") sessions.
Written in Kotlin with XML Views + ViewBinding (no Jetpack Compose),
MVVM, manual DI through `AppContainer`, coroutines and `StateFlow`.
Talks to a plain PHP + MySQL backend.

Target: an NCC-level L5 individual assignment. The written report
accompanies this repository.

## Features

- **Home** — week strip that pulls down into a swipeable month calendar
  (lime dots on active days); pick any day to see that week's sessions /
  active time / distance / kcal and the day's workouts; streak pill and
  personalised insight card.
- **Logging** — a "Log a workout" sheet with swipeable activity cards
  (Running, Cycling, Swimming, Rowing, Workout); GPS or manual entry for
  runs, rides and rows; a Log a swim screen (pool length × laps, live
  pace per 100 m); a strength log with sets. Calories are worked out from
  MET values and the profile weight and saved with each workout.
- **History** — activity filter chips, card rows and a section-headered
  list of past workouts with a weekly momentum chart. Tap a row to open the detail screen (map and stats
  for GPS workouts).
- **Live GPS tracking** — `FusedLocationProviderClient` behind a
  foreground service; three-filter noise suppression (accuracy, min
  step, teleport); osmdroid map with a lime polyline; pause / resume /
  stop with on-screen save. Works with no connection: the route is
  stored on the phone until it syncs. See `docs/dev-log.md` phase 8a
  for the design decisions.
- **Goals** — distance / sessions / time / weight targets over a chosen
  number of days (1–365), created on a one-hand New goal sheet with a
  live summary and estimated calorie burn; Active / Completed / Ended
  sections with a featured main goal; a confetti celebration pop-up the
  first time a goal is completed.
- **Progress + analytics** — period selector (Week / Month / Year),
  activity chips, period bar chart, trend chart (pace / speed /
  volume), per-activity statistics cards, lifetime personal records.
- **Profile** — avatar upload with Android photo picker, editable
  name / weight / height, BMI with category, three summary tiles,
  account deletion confirmed by typing DELETE, notification switches.
- **Offline logging + sync** (phase 10) — workouts are saved in an
  on-phone Room database first; a WorkManager job uploads anything
  saved offline once the phone is back online. Unsynced workouts show
  a cloud-off marker.
- **Notifications** (phase 10) — an optional daily reminder at a
  chosen time (skipped if you already logged a workout that day) and
  a "Goal achieved" alert when the app is in the background (the
  celebration pop-up shows instead when it is open). Both are local notifications posted by the
  phone; there is no Firebase push.

## Required components (assignment brief)

- **RecyclerView** — `fragment_workout_history.xml`, `fragment_goals.xml`, `fragment_log_strength_workout.xml`, Home's day list, and the ViewPager2 activity carousels (RecyclerView-based).
- **FloatingActionButton** — History FAB (opens the Log a workout sheet) + Goals FAB.
- **Geolocation** — `app/src/main/java/com/elijah/fitness/tracking/`
  (engine, service, filter constants) + `ui/tracking/LiveTrackingFragment`.

The three inheritance hierarchies the written report explains:

1. `Workout` → `DistanceWorkout` → `RunningWorkout` / `CyclingWorkout` / `SwimmingWorkout` / `RowingWorkout` (+ `StrengthWorkout`).
2. `BaseFragment<VB : ViewBinding>` — every fragment extends it.
3. `BaseViewHolder<T>` → the workout, goal, calendar and activity-card view holders.

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
  `fitness-api/` folder into XAMPP's `htdocs`, then import
  `backend/sql/01_schema.sql` and `backend/sql/02_seed.sql` via phpMyAdmin.
  `database/fitness_app_demo.sql` (in the submission zip) is a full dump
  of the demo database, including swims, rows and GPS routes.

### 2. Android app

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleLocalDebug
./gradlew installLocalDebug          # requires a running emulator / device
```

The project is Android Studio-friendly: open the repository root, let
Gradle sync, and press **Run**. There are two build flavors on the `env`
dimension:

- `local` — `BASE_URL = http://localhost:8080/` (Docker on the host,
  reached from the device via `adb reverse tcp:8080 tcp:8080`).
  Edit the string in `app/build.gradle.kts` if your backend lives elsewhere.
- `cloud` — `https://elijah-fitness-api.onrender.com/`, the live Render
  deployment (free tier: it sleeps after 15 min idle and takes ~1 min to
  wake; the app pings `/health` at launch to warm it). Deployment is
  defined by `render.yaml` + `backend/Dockerfile` (CI/CD: every push to
  `main` that touches `backend/` redeploys), with the database on Aiven
  MySQL. See `backend/README.md` "Option C" for details.

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
│   │   ├── data/local/db/         Phase 10 — Room offline store
│   │   ├── data/sync/             Phase 10 — WorkManager upload job
│   │   ├── data/repository/       WorkoutRepository, GoalRepository, …
│   │   ├── tracking/              Phase 8a — location engine, foreground service, GPX utilities
│   │   ├── analytics/             Phase 8 — pure-Kotlin Progress calculations
│   │   ├── insights/              Phase 8 — personalised-insights rule engine
│   │   ├── notifications/         Phase 10 — daily reminder + goal alerts
│   │   └── ui/                    Fragments + view-models, one package per screen
│   └── src/test/java/…            509 pure-JVM unit tests
├── backend/               PHP + MySQL API (framework-free)
│   ├── fitness-api/               The actual API (Composer-free)
│   ├── sql/                       Schema + seed + migrations
│   └── api-tests.http             End-to-end request cases (REST Client format)
├── docs/
│   ├── dev-log.md                 Per-step design journal
│   ├── design/activities-v3/      HTML design mock-ups for the latest screens
│   ├── testing/sample_route.gpx   GPX fixture for the live-tracking demo
│   ├── licenses/                  Font licence
│   └── assignment-coverage.md     Brief requirements → implementation map
└── gradle/libs.versions.toml      Dependency catalogue
```

## Known limitations

- **Goals are read-only offline.** Workouts can be logged, edited and
  deleted with no connection (Room + WorkManager, phase 10) and goal
  progress still updates, but creating or editing a goal needs the
  server.
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

## Third-party licences

- **Plus Jakarta Sans** — bundled in `app/src/main/res/font/` as five TTF
  files (Regular / Medium / SemiBold / Bold / ExtraBold). Licensed under
  the SIL Open Font License 1.1; the licence text sits at
  [`docs/licenses/plus-jakarta-sans/OFL.txt`](docs/licenses/plus-jakarta-sans/OFL.txt).
