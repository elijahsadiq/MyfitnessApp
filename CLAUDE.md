# CLAUDE.md — MyFitnessApp

Guidance for future Claude sessions working in this repository. Full phase-1
brief lives in `phase-1-foundation.md`; the phase-2 brief lives in
`docs/prompts/phase-2-backend-and-ui.md`; incremental notes live in
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
| 1  (done) | Foundation, domain models, fake repo, app shell, history + FAB, manual logging, dashboard |
| 2  (done) | PHP/MySQL backend, Retrofit, register/login, DataStore token, `AuthActivity` |
| 7  (done) | Goals (hierarchy #4), per-user progress calculator, Goals tab UI, completion celebration |
| 8a (done) | **Geolocation end-to-end** — foreground service, FusedLocationProviderClient, osmdroid map, route save + detail screen (hoisted ahead of phase 8 polish because the brief weights it at 40 marks) |
| 8  (done) | Goal expiry as a third state, Home redesign (BMI → kcal, real streak, Goals section removed), Profile backend + rebuild, Progress + analytics screen, personalised insights, submission readiness |
| future    | Room offline cache + WorkManager sync (closes the "process-kill drops the GPS session" caveat); extra activity types if the assignment report calls for them |

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

---

## Kinetic Glow design system (from phase 2, Step 1)

Single design system, single accent (electric lime). **Light-only** —
`values-night/` is intentionally absent; `FitnessApp.onCreate()` forces
`AppCompatDelegate.MODE_NIGHT_NO`.

Sources cross-checked: `docs/design/kinetic_glow_fitness/DESIGN.md`
frontmatter, plus the Tailwind config in `home_dashboard/code.html` and
`history_screen/code.html`. Where the DESIGN.md prose disagreed with the
machine-readable sources, we followed the machine sources — with the
following exceptions noted in the palette below.

### Palette (semantic tokens)

| Token | Hex | Used for |
|---|---|---|
| `background_warm` | `#F9FAF6` | canvas |
| `surface_white` | `#FFFFFF` | resting cards, input fields |
| `surface_container_low` | `#F3F4F0` | row icon tiles |
| `surface_container_high` | `#E7E9E5` | section pills, dividers-as-fill |
| `surface_dark` | `#121411` | Home hero card, week-strip active pill |
| `surface_charcoal` | `#1C1F1A` | alternate dark surface (per DESIGN.md prose) |
| `inverse_surface` | `#2E312F` | History "Weekly momentum" card (`colorSurfaceInverse`) |
| `text_primary` | `#1A1C1A` | body text on light |
| `text_secondary` | `#5D5F5B` | muted / metadata |
| `text_on_lime` | `#111410` | **strict rule** — any text drawn on `#D4F26A` |
| `text_on_dark` | `#FFFFFF` | text on dark hero |
| `divider` | `#E4E6E0` | 1dp hairlines, card strokes (also `colorOutlineVariant`) |
| `outline` | `#767965` | M3 `colorOutline` role |
| `lime_accent` | `#D4F26A` | primary CTA, FAB, active nav pill, selected chip, progress ring |
| `lime_deep` | `#536600` | M3 `colorPrimary` (surface tint) — never on button labels |
| `error_material` | `#BA1A1A` | M3 `colorError` |
| `error_red` | `#D64545` | form-validation red (per DESIGN.md prose) |

Deleted from phase 1: `brand_*` teal/orange palette, `accent_running/cycling/weightlifting`. The single-accent lime replaces per-activity tinting; all row icons render in `text_primary` on a `surface_container_low` tile.

### Typography

**Plus Jakarta Sans**, wired via Downloadable Fonts (`res/font/plus_jakarta_sans*.xml` — Google Fonts provider). No fonts are bundled in the APK; falls back to system sans-serif while resolving.

| TextAppearance | Size | Weight | Line height | Tracking |
|---|---|---|---|---|
| `Fitness.DisplayMetric` | 44sp | ExtraBold (800) | 48sp | −0.03em |
| `Fitness.HeadlineLg` (screen title) | 28sp | Bold (700) | 34sp | −0.02em |
| `Fitness.HeadlineSm` (section) | 18sp | SemiBold (600) | 24sp | −0.01em |
| `Fitness.BodyLg` | 15sp | Regular (400) | 22sp | 0 |
| `Fitness.BodyMd` | 14sp | Regular (400) | 20sp | 0 |
| `Fitness.LabelMd` | 13sp | Medium (500) | 18sp | +0.01em |
| `Fitness.LabelSm` | 11sp | SemiBold (600) | 14sp | +0.04em, ALL CAPS |
| `Fitness.Button` | 16sp | SemiBold (600) | — | 0, non-caps |

### Spacing + radii (`res/values/dimens.xml`)

- Scale: `space_xs` 4 · `space_sm` 8 · `space_md` 12 · `space_lg` 16 · `space_xl` 24 · `space_2xl` 32.
- Screen margin `20dp`; section gap `24dp`; touch target min `48dp`; row min height `56dp`; hairline stroke `1dp`.
- Radii: card `24dp`, row `20dp`, text field `16dp`, icon tile `14dp`, bottom-sheet top `28dp`, pill 50%.
- Button pill height `52dp`; FAB `56dp`; bottom nav `64dp`.

### Component styles (`res/values/styles.xml`)

`Widget.Fitness.Button.Primary` (lime pill) · `Widget.Fitness.Button.Outlined` (white pill, 1dp divider border) · `Widget.Fitness.Button.Text` · `Widget.Fitness.Card` (white 24dp radius) · `Widget.Fitness.Card.Dark` (surface_dark hero) · `Widget.Fitness.Card.Inverse` (inverse_surface momentum card) · `Widget.Fitness.Chip.Filter` (selects lime) · `Widget.Fitness.TextField` (16dp radius outlined) · `Widget.Fitness.IconTile` (44×44 squircle) · `Widget.Fitness.FAB` (lime circle) · `Widget.Fitness.BottomNav` (+ `ActiveIndicator` lime pill).

**Layout rule from Step 1 onwards:** every layout resolves colours, sizes and radii through these styles / tokens. No hardcoded hex, sp or dp in any layout file.

### Icons

Material Symbols style, vector drawables at 24dp. Phase-2 adds: `ic_home_24`, `ic_trending_up_24`, `ic_add_24`, `ic_search_24`, `ic_calendar_24`, `ic_notifications_24`, `ic_location_pin_24`, `ic_arrow_forward_24`, `ic_check_24`, `ic_play_arrow_24`, `ic_visibility_24`, `ic_visibility_off_24`, `ic_mail_24`, `ic_lock_24`, `ic_scale_24`. Phase-1 icons kept: `ic_run_24`, `ic_bike_24`, `ic_dumbbell_24`, `ic_history_24`, `ic_flag_24`, `ic_person_24`, `ic_dashboard_24`.

### Onboarding imagery

`res/drawable/img_onboarding_cycling.webp` and `res/drawable/img_onboarding_progress.webp` — WebP conversions of `docs/design/atmospheric_cinematic_.../screen.png` and `docs/design/moody_dark_abstract_.../screen.png`. Downsized to 768×1376 (edge screen density) and quality 80; 43 KB + 27 KB. Source attribution belongs in the phase-2 report.
