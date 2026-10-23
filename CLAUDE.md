# CLAUDE.md — MyFitnessApp

Guidance for future Claude sessions working in this repository. The old
phase briefs are no longer kept in the repo; the full history of what was
built and why lives in `docs/dev-log.md`. Keep this file in sync when
foundational things change.

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
     `CyclingWorkout` / `SwimmingWorkout` / `RowingWorkout` (domain model).
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
| 9  (done) | Full-app audit — GPS crash fix, consistent content height (new shell), One UI thumb-zone layout, branding pass, brand launcher icon, sign-out cache clearing, copy + plurals cleanup. Step 2: cloud deployment (Render + Aiven MySQL), avatars moved into MySQL |
| 10 (done) | **Offline logging + sync** (Room store in `data/local/db/`, `OfflineFirstWorkoutRepository`, WorkManager `WorkoutSyncWorker`) and **local notifications** (`notifications/`: daily reminder, goal-achieved alert, Profile switches) |
| 8d (done) | Swimming + rowing (`SwimmingWorkout` / `RowingWorkout`, migration 08, Room v2), calories stored per workout, activities-v3 screens (swipeable log sheet, Log a swim, one-hand New goal sheet, History restyle), goal-complete celebration (`GoalCelebrationManager`) |
| future    | Journal the live GPS session to Room (closes the "process-kill drops the GPS session" caveat); offline goal edits; extra activity types if the assignment report calls for them |

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
# Two product flavours (local / cloud), so the plain testDebugUnitTest
# task name is ambiguous — name both:
./gradlew testLocalDebugUnitTest testCloudDebugUnitTest
```

## App shell, insets and snackbars (from phase 9)
- `activity_main.xml` is a plain vertical stack inside `mainRoot`:
  AppBarLayout (GONE except on `MainActivity.TOOLBAR_DESTINATIONS` —
  Log run, Log lift, Profile, New goal) → `FragmentContainerView`
  (`0dp`, weight 1) → `BottomNavigationView`. **Do not** go back to
  `appbar_scrolling_view_behavior`: with the toolbar GONE it keeps a
  stale offset and the content height changes after visiting Profile.
- `BaseFragment` inset helpers (`applyInsets`, `applySystemBarInsets`,
  `applyBottomNavInset`, `applyStatusBarMargin`) return insets
  **unconsumed** — Android 8–10 stop dispatching after the first
  consumer. Tab screens clear only the top (`applyBottom = false`);
  the bottom nav handles the bottom.
- Snackbars go through `BaseFragment.snackbar(...)`. Anchor order: the
  explicit `anchor` argument → `snackbarAnchorView()` (override it to
  return a FAB or pinned Save bar) → the bottom nav via `SnackbarHost`.
- Never add children to osmdroid's `MapView` (needs geo-anchored
  `MapView.LayoutParams`, NPEs otherwise — this was the GPS crash). Put
  overlays beside the map in the screen layout.
- Activities are portrait-only on phones (manifest). Android 16+
  ignores this on large screens, so layouts must still not assume a
  fixed width.
- Sign-out always goes through `AppContainer.signOut` /
  `signOutLocally`, which stop tracking, stop goal alerts, cancel the
  reminder and clear every cache (including the Room database) before
  dropping the token.

## Offline store + notifications (from phase 10)
- Screens read workouts from Room through `OfflineFirstWorkoutRepository`;
  writes land in Room first with a `SyncState`, then upload. Never write
  a workout row with `INSERT OR REPLACE` (the FK cascade deletes its sets
  and route) — use the DAO's `@Upsert` / `save`.
- Anything that talks to the server inside the repository holds its
  `syncLock`; local edits bump `localVersion` instead of locking.
- A Room schema change needs a version bump and a `Migration`
  (`exportSchema = false` for v1).
- Notifications are local (`AppNotifications`): channels `reminders` and
  `achievements`, small icon `ic_notification_pulse`. The daily reminder
  is a self-rescheduling one-off WorkManager job, not a periodic one.
- Every save path stores `caloriesKcal` at save time
  (`CalorieEstimator.storedWorkoutKcal` at `UserSettings.weightForSavingKgOrNull`).
  Since phase 8d the profile weight is also kept on the device
  (`ProfileWeightStore`, DataStore `profile_prefs`, via
  `ProfileWeightPersistence`), restored at app start so offline saves after
  a cold start still store calories; sign-out clears it.

## Cloud deployment (from phase 9)
- API: Render free Docker web service `elijah-fitness-api` (Singapore),
  defined in `render.yaml`. It builds `backend/Dockerfile`, the same
  image docker-compose builds locally (compose mounts the live code on
  top). Pushes that touch `backend/` redeploy it automatically.
- Database: Aiven free MySQL 8.4 (Singapore). Connection settings are
  Render environment variables only (`DB_HOST`, `DB_PORT`, `DB_USER`,
  `DB_PASS`) — **never commit them**. `DB_SSL_CA` turns on
  certificate-verified TLS using `backend/certs/aiven-ca.pem`.
- Aiven defaults to ANSI SQL mode. `core/Database.php` pins MySQL 8's
  default `sql_mode` per connection; still write SQL with single-quoted
  strings only.
- A schema change means: update `01_schema.sql`, add a numbered
  migration, and apply that migration to Aiven as well as local Docker.
- Render's disk is wiped on every restart: store nothing as files.
  Avatars live in `users.avatar_jpeg` and are served by
  `GET /avatars/{name}.jpg`.
- Free Render sleeps after 15 minutes idle (~1 min to wake). The app's
  read timeout is 90 s and `AppContainer.wakeUpServer()` pings `/health`
  at launch — keep both.
- The `cloud` flavour's `BASE_URL` is `https://elijah-fitness-api.onrender.com/`.

## How to work in this repo
- One step at a time.
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
| `background_warm` | `#F5F6F2` | canvas (activities-v3 value since phase 8d) |
| `surface_white` | `#FFFFFF` | resting cards, input fields |
| `surface_container_low` | `#F1F3EC` | icon tiles (activities-v3 value since phase 8d) |
| `surface_container_high` | `#E7E9E5` | section pills, dividers-as-fill |
| `surface_dark` | `#121411` | Home hero card, week-strip active pill |
| `surface_charcoal` | `#1C1F1A` | dark cards, selected carousel card (phase 8d) |
| `inverse_surface` | `#2E312F` | History "Weekly momentum" card (`colorSurfaceInverse`) |
| `text_primary` | `#1A1C1A` | body text on light |
| `text_secondary` | `#6B7066` | labels, captions on light (activities-v3 value since phase 8d) |
| `text_on_lime` | `#111410` | **strict rule** — any text drawn on `#D4F26A` |
| `text_on_dark` | `#FFFFFF` | text on dark hero |
| `divider` | `#E4E6E0` | 1dp hairlines, card strokes (also `colorOutlineVariant`) |
| `outline` | `#767965` | M3 `colorOutline` role |
| `lime_accent` | `#D4F26A` | primary CTA, FAB, active nav pill, selected chip, progress ring |
| `lime_deep` | `#536600` | M3 `colorPrimary` (surface tint) — never on button labels |
| `error_material` | `#BA1A1A` | M3 `colorError` |
| `error_red` | `#D64545` | form-validation red (per DESIGN.md prose) |
| `surface_dark_tile` | `#2A2E27` | stat tiles inside dark cards (phase 8d) |
| `text_muted_on_dark` | `#B4B9AE` | secondary text on dark cards (phase 8d) |
| `divider_soft` | `#ECEEE7` | thin row dividers (phase 8d) |
| `dot_inactive` | `#D3D6CD` | carousel dots, sheet drag handles (phase 8d) |
| `text_placeholder` | `#8A8F84` | placeholder / hint text (phase 8d) |
| `text_body_muted` | `#555A50` | muted body copy on light (phase 8d) |
| `text_pill` | `#454A40` | text inside pale pills (phase 8d) |
| `confetti_lime_mid` / `confetti_lime_soft` | `#C6E85A` / `#E8F7B0` | goal-complete confetti (phase 8d) |
| `scrim_celebration` | `#9E111410` | 62 % scrim behind the goal pop-up (phase 8d) |

Deleted from phase 1: `brand_*` teal/orange palette, `accent_running/cycling/weightlifting`. The single-accent lime replaces per-activity tinting; all row icons render in `text_primary` on a `surface_container_low` tile.

### Typography

**Plus Jakarta Sans**, bundled as five TTFs in `res/font/` (OFL licence in `docs/licenses/plus-jakarta-sans/`) behind the `plus_jakarta_sans*` font resources.

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

Shared since phase 9 — reuse these instead of restyling per screen:
- Screen chrome: `ScreenHeader` / `ScreenTitle` (tab titles), `Toolbar`, `TopLoadingBar`.
- Forms: `FormSectionTitle`, `WhenCard` / `WhenCardValue`, `FormError`, `PinnedButtonBar` (bottom Save bar), `Button.Segment`. (The duration cells became `DurationInputView` in phase 8d, below.)
- Metrics: `HeroMetricValue`, `TrackingStatValue`, `SummaryStatValue` — auto-sizing; fill them with `util/MetricText.of(value, unit, …)` so number + unit shrink together.
- Map: `MapOverlayButton` (48dp), `MapAttribution`.
- Sheets / dialogs: `ThemeOverlay.Fitness.BottomSheetDialog`, `Widget.Fitness.BottomSheet`, `MaterialAlertDialog.Fitness.Title`; dialogs and sheets are white (`elevationOverlayEnabled=false`).
- Scrolling lists under a FAB end with `@dimen/fab_clearance` (88dp) of bottom padding.

Shared since phase 8d (activities-v3, `ui/components/`) — the log sheet,
Log a swim and New goal reuse these:
- `ActivityCarouselView` (ViewPager2 cards with peeking neighbours; size from
  `ActivityCardSpec.LOG_SHEET` / `GOAL_SHEET`), `ActivityCardAdapter` +
  `ActivityCardViewHolder : BaseViewHolder`, `CarouselDotsView`,
  `CarouselNavButtons` (+ `Widget.Fitness.RoundIconButton`).
- `PillSegmentedControl` (separate pills with a gap, radio semantics) with
  `Widget.Fitness.PillSegmented.LogSheet` / `.PoolLength` / `.MeasureBy`.
  Don't use `MaterialButtonToggleGroup` for these: it squares the inner corners.
- `Widget.Fitness.Button.Primary.Large` (56dp lime CTA, 16sp ExtraBold),
  `Widget.Fitness.HeaderBackButton`, `Widget.Fitness.SheetHandle`,
  `TextAppearance.Fitness.SheetTitle` / `PageTitle` / `SheetSubtitle` / `SheetNote`.
- In these screens primary text is `text_on_lime` (`#111410`, the designs'
  ink colour); lime is still never used as text on a light surface.
- `StepperView` (− value +, 44dp circles in 48dp targets, hold-to-repeat via
  `HoldToRepeat` / `HoldRepeatSchedule`, minus/plus disable at the limits,
  value is a polite live region). It only reports ±1 steps: the ViewModel
  owns the value and limits and sends back `valueText` + `setStepEnabled`.
  `stepperValueTextSize` / `stepperValueMinWidth` (28sp / 48dp by default).
- `DurationInputView` — the h / min / s cells of **every** log form (swim,
  run / ride / row, workout): white 20dp cards, 26sp ExtraBold number with
  its unit after it, 2dp dark border on the focused cell (`bg_input_card`).
  Validation is shared too: `ui/log/LogFormRules` (start not in the future,
  minutes / seconds 0–59, total > 0 and at most 24 h — the server's limit)
  and `ui/log/LogDateTimePickers` (the Date / Start time pickers and
  formatters; pickers live in the form's child fragment manager, ignore a
  second tap, and each form calls `reattach` in `onViewCreated`).
- Log a swim form blocks: `Widget.Fitness.SummaryCard` / `SummaryTile` /
  `SummaryIconTile` (dark live summary, 26dp radius), `Widget.Fitness.FieldCard`
  (tappable white 20dp card), `Widget.Fitness.NotesField`, and
  `TextAppearance.Fitness.SectionLabel` (12sp Bold caps) / `FieldLabel` /
  `FieldValue` / `FieldHint` / `SummaryHero` (+ `.OnDark` variants). The 50sp
  hero number is 6.5dp taller than the design's `line-height: 1` per side,
  so its gaps use `summary_hero_gap` (9.5dp) rather than 16dp.
  `Widget.Fitness.SectionLabel` is the same label as a TalkBack heading.
- New activities-v3 text styles parent on `TextAppearance.Fitness.Flat`
  (BodyMd with letter spacing 0 and no font padding), never on BodyMd
  directly: M3's BodyMedium adds 0.018em tracking and the font padding makes
  lines ~1.55em tall instead of the designs' 1.26em ("normal").
- The designs' 1px CSS borders sit outside the padding; Android strokes
  inside it, so card paddings are the design value + 1dp.
- Activity cards use `ActivityLabels.cardIconRes` (2dp-stroke glyphs as in
  the designs); the rest of the app keeps `ActivityLabels.iconRes`.
- "Last activity logged" (the log sheet's starting card) is written only by
  a successful save, through `AppContainer.rememberLoggedActivity`.

**Widget fonts:** AppCompat lets the theme's `fontFamily` override the font
from a `textAppearance`, so any style that needs a non-regular weight sets
both `fontFamily` and `android:fontFamily` directly (see the button styles).

**Layout rule from Step 1 onwards:** every layout resolves colours, sizes and radii through these styles / tokens. No hardcoded hex, sp or dp in any layout file.

### Icons

Material Symbols style, vector drawables at 24dp. Phase-2 adds: `ic_home_24`, `ic_trending_up_24`, `ic_add_24`, `ic_search_24`, `ic_calendar_24`, `ic_notifications_24`, `ic_location_pin_24`, `ic_arrow_forward_24`, `ic_check_24`, `ic_play_arrow_24`, `ic_visibility_24`, `ic_visibility_off_24`, `ic_mail_24`, `ic_lock_24`, `ic_scale_24`. Phase-1 icons kept: `ic_run_24`, `ic_bike_24`, `ic_dumbbell_24`, `ic_history_24`, `ic_flag_24`, `ic_person_24`, `ic_dashboard_24`.

Launcher icon (phase 9): adaptive icon in `mipmap-anydpi/ic_launcher*.xml` — `ic_launcher_background` (`surface_dark` tile), `ic_launcher_foreground` (lime pulse) and `ic_launcher_monochrome` (Android 13+ themed icons). There are no per-density webp mipmaps any more (minSdk 27 always uses the adaptive XML). `ic_brand_mark` is the same mark as an in-app logo tile.

### Onboarding imagery

`res/drawable/img_onboarding_cycling.webp` and `res/drawable/img_onboarding_progress.webp` — WebP conversions of `docs/design/atmospheric_cinematic_.../screen.png` and `docs/design/moody_dark_abstract_.../screen.png`. Downsized to 768×1376 (edge screen density) and quality 80; 43 KB + 27 KB. Source attribution belongs in the phase-2 report.
