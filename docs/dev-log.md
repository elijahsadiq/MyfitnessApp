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

## Step 5 — App shell, theme, BaseFragment (inheritance example #2)
**Built**
- Migrated the theme from `Theme.MaterialComponents.DayNight.DarkActionBar` to **Material 3** `Theme.Material3.DayNight.NoActionBar`. Two-file palette:
  - `values/colors.xml` — light: teal primary (`#0F766E`), orange secondary (`#EA580C`), cyan tertiary (`#0891B2`).
  - `values-night/colors.xml` — same hues at dark-mode lightness.
  - Per-activity accents (`accent_running`, `accent_cycling`, `accent_weightlifting`) with light and dark variants.
- Seven vector drawables: `ic_run_24`, `ic_bike_24`, `ic_dumbbell_24` (activity accents) and `ic_dashboard_24`, `ic_history_24`, `ic_flag_24`, `ic_person_24` (bottom nav).
- Navigation graph `nav_graph.xml` with four destinations wired to the four fragment classes; bottom-nav menu (`menu/bottom_nav_menu.xml`) uses matching item ids so `setupWithNavController` handles selection.
- `activity_main.xml` — `CoordinatorLayout` with a `MaterialToolbar` in an `AppBarLayout`, a `FragmentContainerView` (NavHostFragment), and a `BottomNavigationView`.
- `BaseFragment<VB : ViewBinding>` (`ui/base/BaseFragment.kt`) — abstract superclass every fragment extends. Owns the binding lifecycle (`inflate` in `onCreateView`, null in `onDestroyView`), and provides `showError(msg)` (Snackbar), `showEmptyState(emptyView, contentView, isEmpty)`, and `collectOnStart(flow, block)` which wraps `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { flow.collect } }`. KDoc calls out inheritance example #2.
- Four placeholder fragments (`DashboardFragment`, `WorkoutHistoryFragment`, `GoalsFragment`, `ProfileFragment`), all extending `BaseFragment`. Profile reads the current `UserSettings.bodyWeightKg` via `collectOnStart`.
- `MainActivity` (`ui/main/MainActivity.kt`) — sets the toolbar, wires the `NavController` to the toolbar (with an `AppBarConfiguration` listing the four top-level destinations) and the bottom-nav view.
- Registered `MainActivity` in `AndroidManifest.xml` as the `LAUNCHER` activity.
- All user-facing text lives in `strings.xml`. Body-weight text uses a formatted resource (`%1$.1f kg`).

**Key design decision**
- **`BaseFragment.onCreateView` is `final`**, and subclasses only implement `inflateBinding(...)`. That forces every fragment to go through the same lifecycle-safe path — subclasses cannot forget to null the binding, and grepping for `_binding = null` finds exactly one place.

**Rejected alternative**
- Copy-paste the ViewBinding inflate/null pattern into every fragment. Rejected: it would repeat 8 lines per fragment across six fragments and lose the didactic value that Task 5b needs (one hierarchy = one story about code reuse).

**Kotlin/Android concepts (for my notes)**
- *Generics with a bound* (`BaseFragment<VB : ViewBinding>`) — the type parameter is constrained to any subtype of `ViewBinding`. Similar to Python's `TypeVar('VB', bound=ViewBinding)`. Lets `binding` be strongly-typed in each subclass with no casts.
- *`final override`* — locks a method so further subclasses can't re-override. Used for `onCreateView` to protect the invariant.
- *`repeatOnLifecycle(STARTED)`* — restarts flow collection each time the fragment's view reaches STARTED, and cancels when it goes STOPPED. The right pattern for one-shot UI state; keeps the fragment from receiving updates while off-screen.
- *`AppBarConfiguration`* — tells the Navigation component which destinations count as "top level" (no Up arrow, drawer icon shows there). Anything not in the set gets an Up arrow automatically.

**Verified**
- `./gradlew assembleDebug` — BUILD SUCCESSFUL. `./gradlew testDebugUnitTest` — still 21 tests, 0 failures. UI verification on-device is a manual step and hasn't been done yet.

**Assignment task supported**
- **Task 5b (inheritance)** — second inheritance hierarchy (`BaseFragment` extended by every fragment).
- **Task 4 (development)** — the shell that Steps 6–8 fill with real content.

## Step 6 — Workout history + RecyclerView + FAB (inheritance example #3)
**Built**
- `util/Formatters.kt` — pure Kotlin formatting helpers (date, duration, distance in km, pace in min/km, speed in km/h). Zone and locale are parameters so tests are stable across environments.
- `ui/base/BaseViewHolder<T>` — abstract superclass for RecyclerView rows that use ViewBinding. `abstract fun bind(item: T)`.
- `ui/history/WorkoutAdapter.kt` — `ListAdapter<Workout, RecyclerView.ViewHolder>` with two view types dispatched by class:
  - `DistanceWorkoutViewHolder` extends `BaseViewHolder<DistanceWorkout>` and renders `RunningWorkout` (pace) or `CyclingWorkout` (speed) with the right icon and accent colour.
  - `StrengthWorkoutViewHolder` extends `BaseViewHolder<StrengthWorkout>` and renders exercise count · set count and total volume.
  - `DiffUtil` uses `id` for identity and `==` (our overridden equality) for contents.
- `ui/history/HistoryViewModel.kt` — `Filter` enum, `MutableStateFlow` for the filter, `combine(workouts, filter)` piped through `stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())`. Factory uses the `viewModelFactory { initializer { ... } }` DSL.
- `ui/history/WorkoutHistoryFragment.kt` — rewrote the Step 5 placeholder: RecyclerView + `ListAdapter`, `ChipGroup` filter (All / Running / Cycling / Weightlifting), empty state, FAB that navigates to the bottom sheet. Uses `BaseFragment.collectOnStart` for the workouts flow and `showEmptyState` for the empty state toggle.
- Row layouts `item_distance_workout.xml` and `item_strength_workout.xml` with proper icons, dates, and per-type accent tint.
- `res/values/plurals.xml` — `history_row_strength_summary` handles both "1 exercise · 1 set" and plural forms cleanly, ready for future locales.
- `ui/log/ChooseActivityBottomSheet.kt` — extends `BottomSheetDialogFragment` (the only fragment in the app that can't extend `BaseFragment`). Presents three tonal buttons and navigates via `action_choose_to_log_distance` or `action_choose_to_log_strength` with the chosen `ActivityType.name` in the argument bundle.
- Stub `LogDistanceWorkoutFragment` and `LogStrengthWorkoutFragment` (Step 7 fills the bodies). The distance stub already reads the passed `activityType` argument and shows the right title ("Log a run" / "Log a ride").
- Nav graph: added `chooseActivityBottomSheet` (as a `<dialog>`), `logDistanceWorkoutFragment`, `logStrengthWorkoutFragment`, and the three actions. `popUpTo` the history destination on both log actions so the bottom sheet doesn't linger in the back stack.
- `MainActivity` now hides the `BottomNavigationView` on log destinations via `NavController.OnDestinationChangedListener`. The Up arrow shows on log destinations automatically because they're not in the `AppBarConfiguration`.
- Tests: `FormattersTest` (8) and `HistoryViewModelTest` (3, using `StandardTestDispatcher` and an in-memory fake repository). Total suite is now **32 tests, 0 failures**.

**Key design decision**
- **Adapter view-type dispatch reads the item's class directly** (`is DistanceWorkout`, `is StrengthWorkout`) rather than an integer flag on the model. That keeps the model side ignorant of the UI and makes adding a new activity type in phase 5 a matter of one `when` branch, not a model-side field.

**Rejected alternative**
- A "merged" single-row layout with conditional views (show pace *or* speed *or* volume in the same slot). Rejected because the row heights and information density are genuinely different between distance and strength, and mixing them makes both harder to style and harder to explain in the Task 5b write-up.

**Kotlin/Android concepts (for my notes)**
- *`ListAdapter` + `DiffUtil`*: RecyclerView's diff-based adapter. On each `submitList`, it computes minimum changes on a background thread and animates them. `areItemsTheSame` gets the "which is which" (`id`), `areContentsTheSame` gets the "did anything change" (`==`).
- *`combine(a, b) { ... }`*: coroutine-`Flow` combinator that emits a new value every time *either* upstream emits. Perfect for "list × filter → filtered list".
- *`stateIn(scope, started, initial)`*: turns a cold Flow into a hot `StateFlow`. `WhileSubscribed(N)` starts collecting when the first subscriber arrives and stops N ms after the last one leaves — ideal for ViewModels that survive config changes but shouldn't churn while off-screen.
- *Bottom sheet == `<dialog>` in the nav graph*: Navigation treats every dialog-flavour destination (including `BottomSheetDialogFragment`) the same way. Actions still work.
- *`AppBarConfiguration` + not-listed destinations*: any destination not in the top-level set automatically gets the Up arrow via `setupWithNavController`.

**Verified**
- `./gradlew assembleDebug` — BUILD SUCCESSFUL. `./gradlew testDebugUnitTest` — 32 tests, 0 failures (added 8 formatter + 3 VM tests). On-device UI verification is manual and hasn't been done.

**Test flake fixed on the way**
- `formatDate` originally asserted with `Locale.UK`, but JDK 20+ ships CLDR 42, which uses "Sept" for the abbreviated form of September in en-GB. Switched to `Locale.US` where "Sep" remains stable.

**Assignment task supported**
- **Task 5b (inheritance)** — third and final inheritance hierarchy (`BaseViewHolder<T>` extended by two typed view holders).
- **Task 4 (development)** — history screen ticks the RecyclerView + FAB requirements from the brief.

## Step 7 — Manual workout logging
**Built**
- **`LogDistanceWorkoutViewModel`** — holds `date` and `time` (picker state that must survive rotation), validates on save, and builds a `RunningWorkout` or `CyclingWorkout` depending on the `ActivityType` passed to the factory. `Clock` + `ZoneId` are constructor params so tests can pin "now" and time zone.
- **`LogStrengthWorkoutViewModel`** — same date/time model plus a `StateFlow<List<StrengthSet>>` mutated via `addSet` / `removeSetAt`. Save requires ≥ 1 set.
- Both VMs emit one-shot events (`Saved` / `ValidationFailed(errors)`) through a `Channel<Event>.receiveAsFlow()` so config-change replays don't fire the same toast twice.
- **`LogDistanceWorkoutFragment`** — date + time via `MaterialDatePicker`/`MaterialTimePicker`, duration H/M/S EditTexts, distance EditText, notes, live pace (running) / speed (cycling) preview computed from `doAfterTextChanged`, and a save button that pipes into the VM. Errors show inline on `TextInputLayout.setError()` and on a small `TextView` under the duration row.
- **`LogStrengthWorkoutFragment`** — same date/time/duration/notes, plus a RecyclerView of `StrengthSet`s. An "Add set" `MaterialButton` opens a `MaterialAlertDialogBuilder` dialog (`dialog_add_set.xml`) that collects exercise / reps / weight and validates (exercise non-blank, reps 1–100, weight 0–500). Each row has a delete button. **No editable text fields live inside the recycled rows.**
- **`StrengthSetAdapter`** — deliberately simple: mutable list + `notifyDataSetChanged`, no DiffUtil. Sets have no stable id, duplicates are legitimate, and the list is short.
- **`MainActivity.showSnackbar(message)`** — anchors a Snackbar on the activity's `CoordinatorLayout` so log fragments can call it *before* `popBackStack()` and the message survives the fragment transition.
- Both log fragments call `MainActivity.showSnackbar(...)` and then `findNavController().popBackStack()` on save — the new workout is already in the shared `MutableStateFlow`, so it renders at the top of History on return.
- **`strings.xml`** — all new labels, hints, error strings, success strings live here. No hardcoded user-facing text. Touch targets meet the 48 dp minimum.
- **Tests** — `LogDistanceWorkoutViewModelTest` (6: happy-path running, happy-path cycling type dispatch, future date rejected, zero duration rejected, distance > 300 km rejected, missing distance rejected) and `LogStrengthWorkoutViewModelTest` (4: happy path, empty sets rejected, `addSet`/`removeSetAt` behavior, future date rejected). Uses `Clock.fixed` for reproducibility. Total suite is now **42 tests, 0 failures**.

**Key design decision**
- **The VM validates against a fixed `Clock` and a fixed `ZoneId`.** That makes "date can't be in the future" a testable invariant — with `Clock.fixed(...)` we can prove that a picked date one day after the fixed instant is rejected without any tolerance for wall-clock drift. Production code passes `Clock.systemDefaultZone()`.

**Rejected alternative**
- Editing sets inline in the RecyclerView row (Notion-style). Rejected — and the spec explicitly warns against it. Recycled `EditText`s are extremely bug-prone (state leaks between rows, IME races) and a dialog is faster to build, easier to validate, and enough for a phase-1 form.

**Kotlin/Android concepts (for my notes)**
- *`MaterialDatePicker` + `MaterialTimePicker`*: fragment-based dialogs. You call `.show(fragmentManager, tag)` and register a listener via `addOnPositiveButtonClickListener { ... }`. The date picker gives you milliseconds since epoch in UTC — I convert with `Instant.ofEpochMilli(...).atZone(UTC).toLocalDate()` so no timezone drift.
- *`Channel<Event>` + `receiveAsFlow()`*: one-shot events for the UI to consume. Unlike a `StateFlow`, values aren't replayed to new collectors — perfect for "workout saved" so it doesn't fire twice on rotation.
- *`Clock.fixed(instant, zone)`*: JVM utility to freeze "now". Every `LocalDate.now(clock)` etc. becomes deterministic — no more sleeping in tests to avoid clock races.
- *`doAfterTextChanged` (from `androidx.core:core-ktx`)*: a Kotlin extension that wraps `TextWatcher.afterTextChanged`. Kept the preview logic imperative because it fires often and building a full Flow pipeline out of `EditText` state is more machinery than the feature deserves.

**Verified**
- `./gradlew assembleDebug` — BUILD SUCCESSFUL (two harmless K2 forward-compat warnings on `@StringRes` annotation targeting; a future Kotlin release will apply the annotation to both param and field automatically).
- `./gradlew testDebugUnitTest` — **42 tests, 0 failures** (added 10 log-VM tests).
- On-device verification is still manual and outstanding.

**Assignment task supported**
- **Task 4 (development)** — completes the manual logging requirement end-to-end for all three activity types.
