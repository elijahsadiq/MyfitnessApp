# Phase 2: Design system, PHP/MySQL backend, and authentication

This is phase 2 of the My Fitness App build. Phase 1 produced the domain models, a fake in-memory repository, the app shell and manual workout logging. This phase does three things:

1. Turn the Stitch design export into a real Android design system.
2. Build the PHP + MySQL backend the assignment requires.
3. Replace the fake repository with the real API, behind a login.

Read `CLAUDE.md` first. Everything in it still applies: **Kotlin, XML Views with ViewBinding, no Jetpack Compose anywhere**, MVVM with manual dependency injection through `AppContainer`, coroutines and `StateFlow`.

---

## Working agreement (unchanged from phase 1)

- **Start with Step 0 only.** Inspect, report, propose a plan, then stop and wait for my approval before editing files.
- One step at a time. After each step: the build passes, you give me a short summary with any new Kotlin/Android concepts explained in one line, you commit with a clear message, and you wait for my "continue".
- Ask before adding any library not listed below.
- Add an entry to `docs/dev-log.md` for every step: what was built, the key design decision, one rejected alternative and why, and which assignment task it supports.
- If anything here conflicts with the actual project, stop and tell me rather than working around it.

Build commands:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## Step 0: Inspect and plan (read-only)

Read these and report back:
- `docs/design/kinetic_glow_fitness/DESIGN.md` — Stitch's design system document
- `docs/design/home_dashboard/code.html` and `docs/design/history_screen/code.html` — for exact colours, spacing and type scale
- `docs/design/*/screen.png` — the rendered screens
- The current state of the app: `CLAUDE.md`, the package tree, the existing theme files, and the fragments built in phase 1

Then report:
1. The full token set you extracted (colours, type scale, radii, spacing), and any place the HTML disagrees with `DESIGN.md` or with the tokens already in `CLAUDE.md`. Flag conflicts; don't silently pick one.
2. Which phase-1 screens will need restyling, and roughly how much work each is.
3. A numbered plan for Steps 1–9 with the files you expect to create.

**Then stop and wait.**

Two known issues with the export, for your awareness:
- Home and History each render a duplicated top bar ("FITNESS / Home" with a broken image icon) above the real screen header. That is a Stitch artifact. **Do not build it.** Keep only the screen header ("Good morning, Alex" / "Your week" with the avatar on the right).
- The logo failed to load in the export, which is why a broken-image placeholder appears.

---

## Step 1: Design system

Translate the Stitch tokens into Android resources. The HTML is a reference for values only; **write native XML, do not port CSS**.

- `res/values/colors.xml`: the raw palette with semantic names (`lime_accent`, `surface_dark`, `surface_charcoal`, `background_warm`, `text_primary`, `text_secondary`, `divider`, `error_red`).
- `res/values/themes.xml` and `res/values-night/themes.xml`: a Material 3 DayNight theme with no action bar. Map the palette onto the M3 colour roles (`colorPrimary`, `colorOnPrimary`, `colorSurface`, `colorSurfaceContainer`, `colorOutlineVariant`, etc.) so standard components pick the style up automatically. Text on lime is always `#111410`.
- `res/values/type.xml`: a type scale using **Plus Jakarta Sans** via downloadable fonts (`res/font/` with the certs in `res/values/font_certs.xml`). Define text appearance styles for: screen title (28sp bold), section heading (18sp semibold), body (15sp), caption/label (13sp medium), and a metric style for the large bold numbers.
- `res/values/dimens.xml`: the 8dp spacing scale, 20dp screen margin, 24dp section gap, and the corner radii (card 24dp, text field 16dp, bottom sheet top 28dp, icon tile 14dp).
- `res/values/styles.xml`: reusable component styles — `Widget.Fitness.Button.Primary` (lime pill), `Widget.Fitness.Button.Outlined`, `Widget.Fitness.Card` (white, 24dp radius, no elevation, 1dp `divider` stroke), `Widget.Fitness.Card.Dark` (`surface_dark`), `Widget.Fitness.Chip.Filter`, `Widget.Fitness.TextField`, `Widget.Fitness.IconTile`.
- Icons: add the activity and navigation icons as **vector drawables** using Android Studio's Material Symbols (Rounded, outlined) — running figure, bicycle, dumbbell, home, history, trending-up, flag, plus, search, calendar, notifications, location pin. Do not extract PNGs from the export.
- Import the two generated images from `docs/design/atmospheric_cinematic_.../screen.png` and `docs/design/moody_dark_abstract_.../screen.png` into `res/drawable/` as **WebP** (Android Studio can convert), named `img_onboarding_cycling` and `img_onboarding_progress`. Note their origin in `docs/dev-log.md` so I can acknowledge the source in my report.

Record the final token table in `CLAUDE.md` so later sessions stay consistent.

**Every layout built from here on uses these styles. No hardcoded colours, sizes or dimensions in any layout file.**

---

## Step 2: Backend scaffolding (Docker + MySQL)

Create a `backend/` folder at the **root of this repository**, so the app and API live in one git repo and one sandbox boundary.

```
backend/
├── docker-compose.yml
├── docker/php/Dockerfile
├── sql/01_schema.sql
├── sql/02_seed.sql
├── README.md
└── fitness-api/          ← this folder is what gets copied into XAMPP's htdocs
```

- `docker-compose.yml`: `mysql:8.4` (with a named volume and `./sql` mounted into `/docker-entrypoint-initdb.d`), a PHP service built from `php:8.3-apache` with `pdo_mysql` and `mod_rewrite` enabled serving `fitness-api` on port 8080, and `phpmyadmin` on port 8081. All images must run on Apple Silicon.
- **Schema** (`01_schema.sql`) — InnoDB, and **declare `utf8mb4` with `utf8mb4_unicode_ci` explicitly**, because the marker will likely import this into XAMPP's MariaDB, which rejects MySQL 8's default collation:
  - `users`: id, email (unique), password_hash, full_name, weight_kg, height_cm, created_at
  - `auth_tokens`: id, user_id (FK, cascade delete), token_hash, expires_at, created_at
  - `workouts`: id, user_id (FK), client_uuid, activity_type (enum), start_time, duration_seconds, distance_meters (nullable), notes, created_at, updated_at — with a **unique key on (user_id, client_uuid)** so the phase-4 sync can never duplicate a row
  - `strength_sets`: id, workout_id (FK, cascade delete), set_order, exercise_name, reps, weight_kg
  - `goals`: id, user_id (FK), activity_type (nullable), goal_type, target_value, period_start, period_end, created_at
  - Indexes on `workouts(user_id, start_time)` and `auth_tokens(token_hash)`
- **Seed** (`02_seed.sql`): one test user (`alex@example.com` / `Password123`, with a **real bcrypt hash**, not a placeholder) and 8–10 workouts across the last 14 days covering all three activity types, so the marker sees a populated app on first run.
- `README.md`: setup for both routes — Docker (`docker compose up -d`) and XAMPP (copy `fitness-api` to `htdocs`, import the two SQL files via phpMyAdmin) — plus the test login and the emulator base URL.

---

## Step 3: PHP API

Plain PHP 8, **no Composer and no framework**, so the marker can run it from XAMPP with zero setup.

```
fitness-api/
├── .htaccess          # route everything to index.php
├── index.php          # front controller / router
├── config.php         # reads env vars, falls back to XAMPP defaults (root / no password)
├── core/              # Database.php (PDO singleton), Response.php, Auth.php, Validator.php
├── controllers/       # AuthController, UserController, WorkoutController
└── models/            # User, Workout
```

Rules:
- **PDO with prepared statements everywhere.** No string-concatenated SQL anywhere in the codebase.
- Passwords use `password_hash()` / `password_verify()`.
- Tokens are opaque: `bin2hex(random_bytes(32))` returned to the client, with only a **hash** stored in `auth_tokens`, plus an expiry. Logout deletes the row.
- Every response is JSON in one consistent envelope: `{"success": bool, "data": ..., "error": {"message": ..., "fields": {...}}}` with correct HTTP status codes (200, 201, 400, 401, 404, 409, 422, 500).
- Server-side validation on every input. Never trust the client. Validation errors come back per field so the app can show them inline.
- All workout queries are scoped by the authenticated `user_id`. A user must never be able to read or modify another user's data.
- Use a transaction when inserting a strength workout with its sets.

Endpoints for this phase:
| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | create account, return token + user |
| POST | `/auth/login` | verify credentials, return token + user |
| POST | `/auth/logout` | delete the current token |
| GET | `/me` | current user profile |
| PUT | `/me` | update name, weight, height |
| GET | `/workouts` | list, newest first, optional `type`, `from`, `to` filters |
| POST | `/workouts` | create (upsert on `client_uuid`) |
| GET | `/workouts/{id}` | single workout with its sets |
| DELETE | `/workouts/{id}` | delete |

Write `backend/api-tests.http` with a request for every endpoint (register → login → create → list), so I can test the API before touching the app.

---

## Step 4: Networking layer

Add these dependencies: Retrofit, OkHttp with the logging interceptor, `kotlinx.serialization` with the Retrofit converter, and DataStore Preferences.

- `buildConfigField` for `BASE_URL` with **two build flavors**: `local` (`http://10.0.2.2:8080/fitness-api/`) and `cloud` (a placeholder for later). Enable `buildConfig = true`.
- `res/xml/network_security_config.xml` permitting cleartext **only** for `10.0.2.2` and `localhost`, applied to the **debug** manifest only. Release builds stay HTTPS-only.
- `TokenStore` backed by DataStore, exposing the token as a `Flow`.
- `AuthInterceptor` adding `Authorization: Bearer <token>` to every request except the auth endpoints.
- `ApiService` interfaces (`AuthApi`, `WorkoutApi`) with DTOs that are separate from the domain models, plus mapper functions between them. Explain in `docs/dev-log.md` why DTOs are kept separate from domain models — it is a design decision worth writing up.
- A `Result`-style wrapper (sealed class: `Success`, `Error`, `NetworkError`) so ViewModels can distinguish "no internet" from "wrong password" and show the right message. **No raw exceptions reaching the UI.**
- Log the interceptor's body **only in debug builds**.

---

## Step 5: Auth flow plumbing

- `AuthActivity` hosts the onboarding, login and register fragments through its own nav graph. `MainActivity` keeps the existing main nav graph.
- Launch routing: on start, check `TokenStore`. No token → `AuthActivity`. Token present → `MainActivity`. Show onboarding only on first ever launch (a DataStore flag).
- `AuthRepository` with `register`, `login`, `logout` and `isLoggedIn`.
- A 401 from any endpoint clears the token and sends the user back to `AuthActivity` — handle this centrally, not in each ViewModel.
- Wire both activities and the repository through `AppContainer`.

---

## Step 6: Onboarding and permission screens

Build from `docs/design/welcome_screen`, `onboarding_screen_2`, `onboarding_screen_3_1`, `onboarding_screen_3_2` and `location_permission_screen`.

- A `ViewPager2` with three fragments, all extending the existing `BaseFragment`, plus a shared page-indicator component (active page = a short lime pill, others small grey dots).
- Screens 1 and 2 have a circular lime arrow button and a Skip button; screen 3 has the "Get started" pill plus an "I already have an account" text link.
- The location permission screen is **priming only**: it explains why location is needed and what the buttons do. **Do not request any runtime permission in this phase** — GPS arrives in phase 3. "Allow location" continues to the next screen for now, with a TODO.
- Dark screens: force light status bar icons on these screens only, and handle window insets so content doesn't sit under the status or gesture bars.

---

## Step 7: Login and Register screens

Build Login from `docs/design/log_in_screen/screen.png` and `code.html`. Register has no Stitch design, so follow the Login pattern exactly: title "Create your account", fields for Full name, Email, Password, Body weight (kg) with the helper text "Used to estimate calories burned", a terms checkbox, a "Create account" pill button, and a link to Login.

- `TextInputLayout` fields with leading icons, and a show/hide toggle on the password field.
- Client-side validation for immediate feedback (email format, password at least 8 characters, weight in a plausible range), with **server-side errors mapped back onto the matching field** so both work together.
- The primary button shows a loading state and is disabled while a request is in flight.
- Network failures show a Snackbar with a Retry action, not a field error.
- Autofill hints, correct IME actions, and `imeOptions` so the keyboard's next/done buttons behave.
- On success: store the token and go to `MainActivity`.

Add unit tests for the validators and for `AuthViewModel`'s state transitions (idle → loading → success/error), using a fake `AuthRepository`.

---

## Step 8: Real repository

- `RemoteWorkoutRepository` implements the existing `WorkoutRepository` interface using `WorkoutApi`.
- `AppContainer` now provides the remote implementation instead of `FakeWorkoutRepository`. **Keep `FakeWorkoutRepository` in the codebase** for tests and offline development, and note in `docs/dev-log.md` that this swap is exactly the benefit the interface was introduced for — good Task 3b material.
- Every screen that reads workouts gains loading, empty and error states.
- Logging a workout now POSTs to the API. If it fails, keep the user's input on screen and show a retry option — do not discard what they typed.
- There is no offline cache yet; that is phase 4. Say so in the dev log.

---

## Step 9: Restyle the phase 1 screens

Rebuild Home and History to match `docs/design/home_dashboard/screen.png` and `docs/design/history_screen/screen.png`:

**Home:** greeting caption, "Your week" title, avatar, streak pill; the week strip (M–S with dates, today as a dark pill, lime dots under days with a workout); the dark "This week" card with the four stats, the lime progress ring and the "Start workout" button; the insight card; and the Goals section with progress bars. Goals data can stay hardcoded for now, with a TODO for phase 5.

**History:** the dark "Weekly momentum" card with the bar chart and the trend pill; the filter chips with a count badge on "All"; workouts grouped by week with section headers showing a per-week summary; the two row types from phase 1 restyled; and the lime FAB.

**Bottom navigation:** Home, History, Progress, Goals, with a lime pill indicator behind the active icon. Progress stays a placeholder (phase 5). Profile opens from the Home avatar and is built from `docs/design/profile_screen`.

Keep the existing `BaseFragment` and `BaseViewHolder` hierarchies intact — they are required for the report.

---

## Definition of done for phase 2

- [ ] `docker compose up -d` starts the backend, and every request in `api-tests.http` succeeds
- [ ] `assembleDebug` and `testDebugUnitTest` pass
- [ ] A fresh install shows onboarding → register → the app, with real data from MySQL
- [ ] Logging out and back in works, and a 401 returns the user to the login screen
- [ ] A workout logged in the app appears in the `workouts` table in phpMyAdmin
- [ ] No Compose, no hardcoded strings, no hardcoded colours in layouts
- [ ] No SQL string concatenation anywhere in the PHP
- [ ] Home and History match the Stitch designs
- [ ] `docs/dev-log.md` has an entry per step

## Out of scope for phase 2
GPS, location permissions, maps, foreground services, Room, WorkManager, notifications, charts on the Progress screen, goals CRUD, and the workout detail screen.

## Final report when phase 2 is complete
1. What was built, in short.
2. The API endpoint table with example request and response bodies.
3. A description of the auth flow from tapping "Log in" through to the token being stored.
4. The security measures implemented, and what is still missing for a production app.
5. Suggested first tasks for phase 3.
