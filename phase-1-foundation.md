# Phase 1: Project foundation and app shell

You are helping me build an Android fitness tracking app for a university assignment (NCC Education Level 5, Mobile App Development). This is **phase 1 of several**. The goal is a clean, well-structured foundation that builds and runs, not a finished app. We will add features incrementally in later phases, so design for that, but do not build ahead of this phase.

---

## 1. Project context

**What the app does (full scope, across all phases):** users register and log in, log workouts (running, cycling, weightlifting as the minimum), set fitness goals, and view statistics and progress over time. All user data will eventually be stored in MySQL through a PHP web service.

**Fixed constraints from the assignment brief (never deviate):**
- Android only, **Kotlin**, built in Android Studio
- **XML Views with ViewBinding. Do NOT use Jetpack Compose anywhere.**
- The app must visibly use **RecyclerView**, **FloatingActionButton** and **geolocation** (geolocation comes in phase 3)
- The backend is **PHP + MySQL** (phase 2). The app never talks to MySQL directly.
- The written report must explain **three uses of inheritance** in my code, so the inheritance hierarchies below are required, not optional.

**About me:** I am strongest in Python and newer to Kotlin and Android. When you use a Kotlin or Android concept with no direct Python equivalent (for example `open`/`abstract`, data classes, companion objects, coroutines, `Flow`, `StateFlow`, ViewBinding, the Fragment lifecycle), add a one-line plain explanation in your step summary.

---

## 2. Target architecture

```
UI (Activities / Fragments, XML layouts)
   ↓ observes StateFlow
ViewModels
   ↓ calls
Repository interface  ←  phase 1: FakeWorkoutRepository (in-memory)
                         phase 2: RemoteWorkoutRepository (Retrofit → PHP API)
                         phase 4: Room cache + WorkManager sync
```

- Pattern: MVVM with a single-activity structure (MainActivity hosting fragments through the Navigation component), plus a separate AuthActivity in phase 2.
- Dependency injection: **manual**, using an `AppContainer` created in a custom `Application` class. No Hilt or Koin; it's simpler to run and simpler to explain in the report.
- Async work: Kotlin coroutines, with `StateFlow` in ViewModels, collected with `repeatOnLifecycle`.
- Dates and times: `java.time` (`Instant`, `LocalDate`, `Duration`).
- Units: metric (km, kg).

### Roadmap (for context only; do not implement future phases)
| Phase | Scope |
|---|---|
| **1 (now)** | Foundation, domain models, fake repository, app shell, workout history list, FAB, manual workout logging, basic dashboard |
| 2 | PHP/MySQL backend, Retrofit, register/login, token stored in DataStore, AuthActivity |
| 3 | GPS live tracking for running and cycling (FusedLocationProvider, foreground service, route map) |
| 4 | Room offline cache, WorkManager sync, reminder and achievement notifications |
| 5 | Goals, analytics charts, personalised insights, extra activity types |

---

## 3. How to work with me

1. **Start with Step 0 only.** Inspect the project, report back and propose a plan. **Do not edit any files until I approve the plan.**
2. Work **one step at a time**. After each step:
   - The build must pass (see the commands below).
   - Give me a short summary: files changed, what they do, and any Kotlin or Android concepts explained.
   - Commit with a clear message, e.g. `feat: add workout domain model hierarchy`. Never push, never rewrite git history.
   - Wait for my "continue" before starting the next step.
3. **Ask before adding any library** not listed in Step 2.
4. Use the **latest stable library versions** compatible with the project's existing Android Gradle Plugin, Kotlin and Gradle setup. Add everything through the version catalog at `gradle/libs.versions.toml`. Follow the plugin setup the project already uses; don't restructure the Gradle files.
5. If something in this prompt conflicts with the actual project (template, SDK levels, plugin setup), **stop and tell me** rather than working around it silently.
6. Keep a design log at `docs/dev-log.md`. For each step, add a short entry with:
   - what was built
   - the key design decision and one alternative you rejected, with the reason
   - which assignment task it supports (Task 3b design, Task 4 development, Task 5b inheritance)

   Keep entries brief bullet notes; I'll write the report myself.

**Build and test commands** (Gradle needs Android Studio's bundled JDK):
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## 4. Steps

### Step 0: Inspect and plan (read-only)
Read `CLAUDE.md` and the project files, then report:
- Whether the project uses the **Empty Views Activity** template (XML layouts). If it's a Compose template, stop immediately and tell me.
- `namespace`, `applicationId`, `minSdk`, `targetSdk`, `compileSdk`, AGP version, Kotlin version.
- If `namespace` is `com.example.*`, ask whether I want to rename it before we create packages.
- If `minSdk` is below 26, propose raising it to 26 (for native `java.time`) and explain the trade-off.

Then propose a numbered plan for Steps 1–8 with the files you expect to create. **Stop and wait.**

### Step 1: Build configuration
- Enable ViewBinding.
- Add only these phase-1 dependencies: Material Components (Material 3), AppCompat, ConstraintLayout, RecyclerView, Fragment KTX, Navigation (fragment + ui), Lifecycle (ViewModel + runtime KTX), Kotlin Coroutines (Android), plus JUnit and `kotlinx-coroutines-test` for unit tests.
- Verify with `assembleDebug`.

### Step 2: Package structure and CLAUDE.md
Create this structure under the app's base package:
```
data/
  model/        domain models (Step 3)
  repository/   WorkoutRepository interface + FakeWorkoutRepository
ui/
  base/         BaseFragment, BaseViewHolder
  main/         MainActivity
  dashboard/    DashboardFragment + DashboardViewModel
  history/      WorkoutHistoryFragment, WorkoutAdapter, view holders, ViewModel
  log/          ChooseActivityBottomSheet, LogDistanceWorkoutFragment, LogStrengthWorkoutFragment, ViewModels
  goals/        GoalsFragment (placeholder)
  profile/      ProfileFragment (placeholder)
util/           CalorieCalculator, formatters
FitnessApp.kt   Application class
AppContainer.kt manual dependency container
```
Update `CLAUDE.md` with the constraints, architecture and roadmap from this prompt so future sessions keep them.

### Step 3: Domain model hierarchy (inheritance example 1)
Pure Kotlin with **no Android imports**, so the models are unit-testable. Implement this hierarchy (signatures are a guide, so refine the names if needed):

```kotlin
enum class ActivityType { RUNNING, CYCLING, WEIGHTLIFTING }

abstract class Workout(
    val id: String,               // UUID generated on the device; becomes the sync key in phase 4
    val startTime: Instant,
    val duration: Duration,
    val notes: String?
) {
    abstract val type: ActivityType
    protected abstract fun metValue(): Double
    open fun caloriesBurned(userWeightKg: Double): Double   // MET × weight (kg) × duration (hours)
}

abstract class DistanceWorkout(/* base params */, val distanceMeters: Double) : Workout(/* ... */) {
    fun distanceKm(): Double
    fun averageSpeedKmh(): Double
}

class RunningWorkout(/* ... */) : DistanceWorkout(/* ... */) {
    fun paceSecondsPerKm(): Long
    // metValue() depends on speed
}

class CyclingWorkout(/* ... */) : DistanceWorkout(/* ... */) {
    // metValue() depends on speed
}

data class StrengthSet(val exercise: String, val reps: Int, val weightKg: Double)

class StrengthWorkout(/* base params */, val sets: List<StrengthSet>) : Workout(/* ... */) {
    fun totalVolumeKg(): Double                           // sum of reps × weight
    fun estimatedOneRepMaxKg(exercise: String): Double?   // Epley: weight × (1 + reps / 30), best set
}
```
Requirements:
- Put all MET values in one `MetValues` object, with a comment saying they come from the *Compendium of Physical Activities*. Mark them `// TODO: verify against Compendium`, because I will check and cite the exact values myself.
- Add KDoc on `Workout` and `DistanceWorkout` explaining **why** each level exists: what it shares with its subclasses and what it leaves abstract.
- Give the models proper equality (or a content-comparison helper) so `DiffUtil` works correctly in Step 6.
- Add **unit tests** for: pace, average speed, total volume, the Epley one-rep max, calorie calculation, and polymorphism (a `List<Workout>` of mixed subclasses returning type-specific calories).

### Step 4: Repository and dependency container
- `interface WorkoutRepository` with `fun observeWorkouts(): Flow<List<Workout>>`, `suspend fun addWorkout(workout: Workout)` and `suspend fun getWorkout(id: String): Workout?`.
- `FakeWorkoutRepository`: in-memory storage using `MutableStateFlow`, seeded with 8–10 realistic sample workouts spread over the last 14 days and covering all three types. Sort newest first.
- A `UserSettings` holder with a default body weight of 70 kg, which will become editable in Profile later.
- `FitnessApp` creates an `AppContainer`. ViewModels get their dependencies through a `ViewModelProvider.Factory` (use the lifecycle `viewModelFactory { initializer { } }` DSL).
- Note in `docs/dev-log.md` that phase-1 data is lost when the app restarts. That's intentional.

### Step 5: App shell, theme and base fragment (inheritance example 2)
- **Theme:** Material 3 DayNight with no action bar. Choose a cohesive, energetic palette with both light and dark variants. Give each activity type a distinct accent colour and a vector icon (Material Symbols style: run, bike, dumbbell).
- **MainActivity:** a `MaterialToolbar`, a `NavHostFragment` and a `BottomNavigationView` with four destinations: Dashboard, History, Goals, Profile. Hide the bottom navigation on the logging form screens, and show the Up arrow there.
- **`BaseFragment<VB : ViewBinding>`**, an abstract class that:
  - handles the ViewBinding lifecycle (inflate in `onCreateView`, set to null in `onDestroyView`)
  - provides shared helpers: `showError(message)` via Snackbar, `showEmptyState(...)`, and a helper for collecting flows with `repeatOnLifecycle`
  - is extended by **every** fragment in the app (the bottom sheet is the exception, since it must extend `BottomSheetDialogFragment`)
  - has KDoc explaining the code it removes from each subclass
- Goals and Profile are placeholders that show a friendly empty state. Profile displays the current body weight used for calorie calculations.

### Step 6: Workout history with RecyclerView and FAB (inheritance example 3)
- `WorkoutHistoryFragment` shows workouts in a **RecyclerView** using `ListAdapter` + `DiffUtil`.
- **Two view types:** a distance row (icon, type, date, distance, duration, pace or speed) and a strength row (icon, date, number of exercises and sets, total volume). The adapter decides the view type polymorphically from the model's class.
- **`abstract class BaseViewHolder<T>(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)`** with `abstract fun bind(item: T)`. `DistanceWorkoutViewHolder` and `StrengthWorkoutViewHolder` extend it.
- A Material `ChipGroup` above the list filters by All / Running / Cycling / Weightlifting.
- Show an empty state when the list or the current filter has no results, e.g. "No workouts yet. Tap + to log one."
- A **FloatingActionButton** with a content description opens `ChooseActivityBottomSheet`, which offers the three activity types. Picking one navigates to the matching log screen, passing the activity type as a navigation argument.
- Tapping a row does nothing yet; a detail screen comes later.

### Step 7: Manual workout logging
**`LogDistanceWorkoutFragment`** (running and cycling):
- Fields: date and time (MaterialDatePicker + MaterialTimePicker, defaulting to now), duration (hours / minutes / seconds), distance in km, optional notes.
- Validation, shown inline on the fields:
  - duration must be greater than 0
  - distance must be greater than 0 and no more than 300 km
  - date can't be in the future
- Show a live preview of pace (running) or average speed (cycling) as the user types.

**`LogStrengthWorkoutFragment`**:
- Fields: date and time, duration, optional notes.
- A RecyclerView of added sets. An "Add set" button opens a small dialog (exercise name, reps 1–100, weight 0–500 kg) that appends the set to the list, and each row has a delete action. **Don't** put editable text fields inside RecyclerView rows; recycling makes them buggy.
- At least one set is required before saving.

**Both screens:**
- Each has its own ViewModel that validates the input and calls `repository.addWorkout(...)`.
- On save, navigate back to History, where the new workout appears at the top, and show a confirmation Snackbar.
- All user-facing text lives in `strings.xml`. Touch targets are at least 48dp.

### Step 8: Dashboard
- `DashboardFragment` shows **this week** (Monday to now, in the device's time zone) as Material cards: number of workouts, active minutes, total distance (running + cycling), and estimated calories (using the default body weight).
- Add a small per-activity breakdown row (count and main stat for each type).
- The numbers update immediately after a workout is logged, because they come from the same repository `Flow`.
- Put the aggregation logic in the ViewModel or a small calculator class, and **unit test** it.

---

## 5. Definition of done for phase 1
- [ ] `assembleDebug` and `testDebugUnitTest` both pass
- [ ] The app launches on an emulator with no crashes and no Compose code anywhere
- [ ] Bottom navigation works across all four destinations
- [ ] History shows seeded workouts with two row layouts, and the filter chips and empty state work
- [ ] The FAB opens the bottom sheet, all three log forms validate input and save, and new workouts appear in History and update the Dashboard
- [ ] All three inheritance hierarchies exist with KDoc: `Workout` models, `BaseFragment`, `BaseViewHolder`
- [ ] No hardcoded user-facing strings, and every icon or FAB has a content description
- [ ] `docs/dev-log.md` has an entry for every step
- [ ] Each step is a separate git commit

## 6. Out of scope for phase 1 (do not build)
Networking, Retrofit, login and registration, the PHP backend, the Room database, WorkManager, notifications, GPS or location permissions, maps, charts, goals logic, a workout detail screen, editing or deleting workouts, and any Compose code.

## 7. Final report when phase 1 is complete
Give me:
1. A short summary of what was built.
2. The package tree.
3. The three inheritance hierarchies as simple text diagrams, with one sentence each on the code reuse they provide.
4. Known limitations and suggested first tasks for phase 2.
