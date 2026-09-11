# CLAUDE.md — MyFitnessApp

Guidance for future Claude sessions working in this repository. Full phase-1
brief lives in `phase-1-foundation.md`; incremental notes live in
`docs/dev-log.md`. Keep this file in sync when foundational things change.

---

## Fixed constraints (assignment brief — never deviate)
- Android only, **Kotlin**, built in Android Studio.
- **XML Views with ViewBinding. Do NOT use Jetpack Compose anywhere.**
- The app must visibly use **RecyclerView**, **FloatingActionButton**, and
  **geolocation** (geolocation lands in phase 3).
- Backend is **PHP + MySQL** (phase 2). The app never talks to MySQL directly.
- The report must explain **three uses of inheritance**, so the following
  three hierarchies are load-bearing, not optional:
  1. `Workout` → `DistanceWorkout` / `StrengthWorkout` → `RunningWorkout` /
     `CyclingWorkout` (domain model, Step 3).
  2. `BaseFragment<VB : ViewBinding>` — every fragment extends it, except the
     `BottomSheetDialogFragment` (Step 5).
  3. `BaseViewHolder<T>` → `DistanceWorkoutViewHolder` /
     `StrengthWorkoutViewHolder` (RecyclerView adapter, Step 6).

## Target architecture
```
UI (Activities / Fragments, XML layouts)
   ↓ observes StateFlow
ViewModels
   ↓ calls
Repository interface  ← phase 1: FakeWorkoutRepository (in-memory)
                       phase 2: RemoteWorkoutRepository (Retrofit → PHP API)
                       phase 4: Room cache + WorkManager sync
```
- Pattern: MVVM, single-activity (`MainActivity` hosts fragments via the
  Navigation component). A separate `AuthActivity` arrives in phase 2.
- DI: **manual**, `AppContainer` created in `FitnessApp`. No Hilt / Koin.
- Async: Kotlin coroutines, `StateFlow` in ViewModels, collected with
  `repeatOnLifecycle`.
- Dates and times: `java.time` (`Instant`, `LocalDate`, `Duration`).
- Units: metric (km, kg). Default body weight 70 kg (editable later).

## Roadmap
| Phase | Scope |
|---|---|
| **1 (in progress)** | Foundation, domain models, fake repo, app shell, history + FAB, manual logging, dashboard |
| 2 | PHP/MySQL backend, Retrofit, register/login, DataStore token, `AuthActivity` |
| 3 | GPS live tracking (FusedLocationProvider, foreground service, route map) |
| 4 | Room offline cache, WorkManager sync, notifications |
| 5 | Goals, analytics charts, personalised insights, extra activity types |

## Package layout (target)
```
com.elijah.fitness
├── FitnessApp.kt           Application, owns AppContainer
├── AppContainer.kt         manual DI container
├── data/
│   ├── model/              domain models (pure Kotlin, no Android imports)
│   └── repository/         WorkoutRepository interface + FakeWorkoutRepository
├── ui/
│   ├── base/               BaseFragment, BaseViewHolder
│   ├── main/               MainActivity
│   ├── dashboard/          DashboardFragment + DashboardViewModel
│   ├── history/            WorkoutHistoryFragment, adapter, view holders, VM
│   ├── log/                ChooseActivityBottomSheet + log fragments + VMs
│   ├── goals/              GoalsFragment (placeholder)
│   └── profile/            ProfileFragment (placeholder)
└── util/                   CalorieCalculator, formatters
```

## Build config quirks
- **AGP 9.4.0**, Gradle 9.6.0, Kotlin bundled with AGP.
  Do **not** apply `org.jetbrains.kotlin.android` — AGP 9 registers the
  `kotlin` extension itself and the plugin fails with
  `Cannot add extension with name 'kotlin'`. Kotlin sources still compile
  because AGP 9 provides Kotlin support natively.
- `compileSdk` 37, `targetSdk` 37, `minSdk` 27 (native `java.time` works),
  Java 11 source/target.
- All dependencies flow through `gradle/libs.versions.toml`. Reference them
  as `libs.androidx.recyclerview` etc. Do not restructure the Gradle files.
- Ask before adding any new library.

## Build and test commands
Gradle needs Android Studio's bundled JDK:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## How to work in this repo
- One phase-1 step at a time (see `phase-1-foundation.md` §4).
- After each step: build must pass, one commit per step, entry appended to
  `docs/dev-log.md`. Never push or rewrite history.
- User is stronger in Python than Kotlin. When touching Kotlin/Android
  concepts with no direct Python equivalent (`open`/`abstract`, data classes,
  companion objects, coroutines, `Flow`, `StateFlow`, ViewBinding, Fragment
  lifecycle), add a one-line plain explanation in the step summary.
- Prefer editing existing files. Everything user-facing goes through
  `strings.xml`. Touch targets ≥ 48 dp. Every icon / FAB needs a content
  description.

## Out of scope for phase 1
Networking, Retrofit, login/registration, PHP backend, Room, WorkManager,
notifications, GPS or location permissions, maps, charts, goals logic,
workout-detail screens, editing/deleting workouts, any Compose code.
