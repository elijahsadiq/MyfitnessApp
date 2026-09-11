# Dev log — Phase 1

Short notes for each step: what was built, the key design decision, one rejected alternative and its reason, and which assignment task the step supports.

---

## Step 0 — Inspection (read-only)
- Confirmed the Android Studio template is Empty Views (XML), not Compose.
- Read `phase-1-foundation.md`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, manifest.
- Baseline: AGP 9.4.0, Gradle 9.6.0, `minSdk 27`, `targetSdk 37`, `compileSdk 37`, Java 11.
- Flagged: `namespace` was `com.example.myfitnessapp`; the manifest declared no activity; no Kotlin plugin was applied even though `.kt` test files exist.
- Supports: **Task 3b (design)** — used to lock the project foundation before diagrams are drawn.

## Step 1 — Build configuration + package rename
**Built**
- Renamed the package from `com.example.myfitnessapp` to `com.elijah.fitness` (namespace, applicationId, source tree under `app/src/{main,test,androidTest}/java/com/elijah/fitness`, and the `useAppContext` assertion in `ExampleInstrumentedTest`).
- Enabled `buildFeatures { viewBinding = true }` in `app/build.gradle.kts`.
- Added phase-1 dependencies through `gradle/libs.versions.toml`: `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material:material` (Material 3), `androidx.constraintlayout`, `androidx.recyclerview`, `androidx.fragment:fragment-ktx`, `androidx.navigation:navigation-fragment-ktx` and `navigation-ui-ktx`, `androidx.lifecycle:lifecycle-viewmodel-ktx` and `lifecycle-runtime-ktx`, `kotlinx-coroutines-android`, and (test-only) `kotlinx-coroutines-test`.
- Verified with `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` — both pass.

**Key design decision**
- **Do not apply the `org.jetbrains.kotlin.android` plugin.** AGP 9.4.0 registers a `kotlin` extension itself, so adding the standalone Kotlin plugin fails with `Cannot add extension with name 'kotlin'`. Kotlin sources still compile because AGP 9 provides Kotlin support natively (`compileDebugKotlin` and `compileDebugUnitTestKotlin` run without the plugin). A comment in `libs.versions.toml` records this.

**Rejected alternative**
- Downgrading to AGP 8.x to use the familiar standalone Kotlin plugin. Rejected because the user asked to keep AGP on 9.x, and downgrading would fight the template Android Studio generated.

**Kotlin/Android concepts (for my notes)**
- *ViewBinding*: a generated typed class per XML layout — replaces `findViewById` and avoids the null-safety and typo risks of `synthetic` imports.
- *Version catalog* (`libs.versions.toml`): a single TOML file that declares versions, library coordinates, and plugin ids. Modules reference them as `libs.androidx.core.ktx` etc., which makes bumps a one-file change.

**Assignment task supported**
- **Task 4 (development)** — the app can now compile and depend on the AndroidX/Material libraries the rest of phase 1 needs. No production code yet.

## Step 2 — Package tree, Application, AppContainer, CLAUDE.md
**Built**
- Created the target package tree under `com.elijah.fitness`:
  `data/{model,repository}`, `ui/{base,main,dashboard,history,log,goals,profile}`, `util`. Empty folders keep a `.gitkeep` so git tracks them until code arrives.
- Added `FitnessApp.kt` — an `Application` subclass that owns `AppContainer` via `by lazy`.
- Added `AppContainer.kt` — an empty manual-DI container that Step 4 will populate with the repository and user settings.
- Wired `android:name=".FitnessApp"` into `AndroidManifest.xml` so the custom Application is instantiated.
- Wrote `CLAUDE.md` with the fixed assignment constraints, target architecture, roadmap, package layout, AGP-9 Kotlin quirk, build commands, and the "how we work" checklist.

**Key design decision**
- **`AppContainer` is a plain class exposed by `FitnessApp` through `by lazy`.** Fragments reach it via `(requireContext().applicationContext as FitnessApp).container`. Simple, no reflection, easy to explain in the report.

**Rejected alternative**
- Hilt (or Koin) for DI. Rejected because phase 1 has one repository and a settings holder — a runtime DI framework would add annotation processing, a codegen step, and paragraphs of explanation for zero real benefit at this scale.

**Kotlin/Android concepts (for my notes)**
- *Application class*: the singleton entry point Android instantiates before any activity. Good place to hold app-lifetime dependencies. Registered in the manifest via `android:name=".FitnessApp"`.
- *`by lazy`*: initialises a `val` on first read and caches the result. Cheap, thread-safe by default. Similar to Python's `functools.cached_property`.

**Verified**
- `./gradlew assembleDebug` — BUILD SUCCESSFUL (11 tasks executed, 25 up-to-date). `compileDebugKotlin` now compiles the two new files.

**Assignment task supported**
- **Task 3b (design)** — the package tree and DI approach in `CLAUDE.md` feed the architecture diagram.
- **Task 4 (development)** — foundation for every later step.

## Step 3 — Domain model hierarchy (inheritance example #1)
**Built**
- `ActivityType` enum (RUNNING, CYCLING, WEIGHTLIFTING).
- `MetValues` object with speed-banded MET tables for running and cycling and a moderate weightlifting constant. Every entry is marked `TODO: verify against Compendium` so exact citations get added when I write the report.
- `Workout` abstract base with `id`, `startTime`, `duration`, `notes`, abstract `type` and `metValue()`, and an `open caloriesBurned(userWeightKg)` implementing `MET × kg × hours`. Overrides `equals` and `hashCode` so content-comparison works for DiffUtil.
- `DistanceWorkout` abstract subclass adding `distanceMeters` plus `distanceKm()` and `averageSpeedKmh()`.
- `RunningWorkout` (adds `paceSecondsPerKm()`, MET from `MetValues.running`) and `CyclingWorkout` (MET from `MetValues.cycling`).
- `StrengthSet` data class, `StrengthWorkout` with `totalVolumeKg()` and Epley `estimatedOneRepMaxKg(exercise)` (case-insensitive match, returns highest estimate or `null`).
- KDoc on `Workout` and `DistanceWorkout` explains "what this level owns" vs "what it leaves abstract", and calls out this hierarchy as inheritance example #1 for Task 5b.
- `WorkoutModelTest` covering pace, average speed, total volume, Epley best-of and null cases, calorie calc, polymorphism over a mixed `List<Workout>`, and equality (positive, negative, cross-type). **12 tests, 0 failures.**

**Key design decision**
- **Concrete classes are plain classes with explicit `equals`/`hashCode`**, not `data class`. Data classes only include their own primary-constructor properties in `equals`, so a data-class subclass of an abstract base would ignore the base's `id`, `startTime`, etc. — the exact fields DiffUtil needs. Manual equals in each level (Workout → DistanceWorkout → StrengthWorkout) makes the semantics obvious and preserves proper cross-type inequality (e.g. a `RunningWorkout` never equals a `CyclingWorkout` with the same base fields).

**Rejected alternative**
- A `sealed class Workout` with concrete `data class` subclasses at one level. Rejected because that flattens the tree: running and cycling would each have to redeclare `distanceMeters`, `distanceKm()` and `averageSpeedKmh()` instead of inheriting them, defeating the point of the intermediate `DistanceWorkout` level (which is precisely the code-reuse the assignment report needs to describe).

**Kotlin/Android concepts (for my notes)**
- `abstract` / `open`: Kotlin classes and members are `final` by default. `abstract class` cannot be instantiated and can have `abstract fun`s that subclasses must implement. `open fun` is the "may be overridden" version of a concrete method — `caloriesBurned` uses this because subclasses share the formula in phase 1 but could refine it later.
- `data class`: auto-generates `equals`, `hashCode`, `toString`, `copy`, and `componentN` from primary-constructor properties. Used here for `StrengthSet` (a plain value carrier) but *not* for the workout classes (see above).
- `sumOf { ... }` and `maxOf { ... }`: Kotlin standard-library collection reductions — similar to Python's `sum(x.foo for x in xs)` and `max(x.foo for x in xs)`.

**Verified**
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL, 12 model tests + the pre-existing example test pass.

**Assignment task supported**
- **Task 5b (inheritance)** — first of the three inheritance hierarchies the report has to describe.
- **Task 4 (development)** — the domain layer that later steps' UI and repository build on.

## Step 4 — Repository and dependency container
**Built**
- `WorkoutRepository` interface: `observeWorkouts(): Flow<List<Workout>>`, `suspend addWorkout(workout)`, `suspend getWorkout(id): Workout?`. Deliberately hides where data lives — phase 2 swaps in Retrofit, phase 4 adds Room, all behind this signature.
- `FakeWorkoutRepository`: in-memory storage on a `MutableStateFlow<List<Workout>>`, always sorted newest first, using `update {}` for atomic thread-safe writes. Seeded with 9 sample workouts spread across 14 days: three runs, three rides, three lifting sessions.
- `UserSettings`: a `MutableStateFlow<Double>` for body weight, defaulting to 70 kg, editable in a future Profile screen.
- Populated `AppContainer` with `workoutRepository` and `userSettings`.
- Tests: `FakeWorkoutRepositoryTest` (6 — seed count, all three types present, newest-first ordering, `addWorkout` insertion at head, `getWorkout` hit and miss); `UserSettingsTest` (2 — default weight, update).

**Important limitation — recorded intentionally**
- Phase-1 workout data lives only in the process. Restarting the app resets the list to the seeded workouts. Room + WorkManager sync replace this in phase 4.

**Key design decision**
- **The repository interface + fake implementation is the only phase-1 seam between UI and data.** ViewModels will depend on the interface, so replacing the fake with a real backend later is a one-line change inside `AppContainer` — no ViewModel or UI code touched.

**Rejected alternative**
- Making the fake repo talk directly through a lambda-based observer pattern rather than a `Flow`. Rejected because `StateFlow` gives us "current value + all future values" for free, is lifecycle-friendly with `repeatOnLifecycle`, and is the API the rest of phase 1 (dashboard, history) is going to consume.

**Kotlin/Android concepts (for my notes)**
- `Flow` / `StateFlow`: coroutine-based observable streams. `StateFlow` always holds a current value and re-emits it to new collectors — great for "here is the current list of workouts". Similar in spirit to RxJava's `BehaviorSubject`, or an observable in the front-end sense.
- `MutableStateFlow.update { current -> newValue }`: atomic read-modify-write. Safer than `value = f(value)` when multiple coroutines could write simultaneously.
- `suspend fun`: a function that can pause without blocking the thread. Called from a coroutine and safe to do I/O in. In phase 1 the fake repo returns immediately, but the signature is already correct for phase 2's network calls.
- Companion `private companion object`: the Kotlin equivalent of Python's class-level statics — lets `seedWorkouts` be a "static" helper attached to the class.

**Verified**
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL. Now 21 tests across four suites (12 model + 6 repo + 2 settings + 1 example), 0 failures.

**Assignment task supported**
- **Task 3b (design)** — repository interface is the seam the architecture diagram highlights.
- **Task 4 (development)** — the data layer that Steps 6–8 collect and display.
