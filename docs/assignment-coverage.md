# Assignment coverage map

A table-by-table map from each requirement in the NCC L5 brief to its
implementation in this repository. The intent is a one-page checklist
the examiner (and I) can scan against the mark scheme.

## Task 4 — development (the 40-point section)

### Required visible components

| Requirement | Status | Where |
|---|---|---|
| **RecyclerView** (visibly used) | ✅ Shipped | `app/src/main/res/layout/fragment_workout_history.xml:184`, `fragment_goals.xml:52`, `fragment_log_strength_workout.xml:191`. Adapters: `WorkoutAdapter`, `GoalsAdapter`, `StrengthSetAdapter`. Screenshot: `docs/screenshots/geolocation_3_route_on_detail_screen.png` (adapter visible in the History row that leads into the detail screen). |
| **FloatingActionButton** (visibly used) | ✅ Shipped | **`fragment_workout_history.xml` → `addWorkoutFab`** (phase 8c step 1 reinstatement) — a `com.google.android.material.floatingactionbutton.FloatingActionButton` styled as `Widget.Fitness.FAB` (lime circle, `ic_add_24`), pinned bottom-right of the History screen, opening the "Log a workout" sheet. The Goals "+ New goal" at the top of the Goals screen is a lime pill button, not a FAB — the History FAB is therefore the sole FloatingActionButton in the app and must not be removed. |
| **Geolocation** | ✅ Shipped | Engine: `app/src/main/java/com/elijah/fitness/tracking/`. Service: `LocationTrackingService.kt` (foreground, `type=location`). UI: `ui/tracking/LiveTrackingFragment.kt` (phase 8c step 6 rebuild — one-screen dark map with Lock + hold-to-unlock, GPS strength pill, pause/resume with visible gap in the polyline), `ui/history/WorkoutDetailFragment.kt` + `FullScreenMapFragment.kt` (phase 8c step 7 — dark map card + full-screen interactive map on expand). Dark map renderer: `ui/tracking/map/` (`DarkMapStyler`, `RoutePolylineRenderer`, `MapCameraController`). Storage: `route_points` table (migration 04). Screenshots: `docs/screenshots/geolocation_*.png` + `tracking-live.png`, `tracking-locked.png`, `workout-detail.png`, `workout-detail-fullmap.png` (phase 8c step 8). |
| **Three inheritance hierarchies** | ✅ Shipped + KDoc'd | `Workout.kt` (hierarchy #1), `BaseFragment.kt` (#2), `BaseViewHolder.kt` (#3). Phase-7 bonus: `Goal.kt` (#4). All four carry their inheritance-example KDoc for the report. |

### Core feature set

| Requirement | Status | Where |
|---|---|---|
| User registration / login | ✅ | `ui/auth/` + `data/remote/api/AuthApi.kt` + `data/repository/RemoteAuthRepository.kt`. SHA-256 token hash in `auth_tokens`. |
| Logging the three activity types | ✅ | `ui/log/LogDistanceWorkoutFragment.kt` (running + cycling), `ui/log/LogStrengthWorkoutFragment.kt`. Entry point: `ChooseActivityBottomSheet` (phase 8a offers Track-with-GPS / Enter-manually for Running+Cycling). |
| Workout history | ✅ | `ui/history/WorkoutHistoryFragment.kt` + section-headered adapter + weekly momentum chart. |
| Workout detail with route | ✅ | `ui/history/WorkoutDetailFragment.kt` (phase 8c step 7 rebuild — self-drawn header with time-of-day title, 2 × 2 stat grid, dark map card with activity pill + expand button, Edit-notes dialog, Delete flow) + `FullScreenMapFragment.kt` (full-screen interactive map). Backend write path: `PATCH /workouts/{id}` → `WorkoutController::update`. Splits block was removed per the Detail.dc.html design. |
| Goals (create / edit / delete) | ✅ | `ui/goals/` + `data/repository/RemoteGoalRepository.kt` + phase-7 schema. |
| "Detailed statistics for each activity" | ✅ | `analytics/ProgressAnalytics.kt::statsFor` + `ui/progress/ProgressFragment.kt` per-activity cards. |
| "Monitor progress over time" | ✅ | `analytics/ProgressAnalytics.kt::activityBars` + `trendBars` + `BarChartView` on Progress tab. |
| Personal records | ✅ | `analytics/ProgressAnalytics.kt::personalRecords` + Progress tab personal-records list. |
| Personalised insights | ✅ | `insights/InsightEngine.kt` (five rules in priority order) + Home insight card binding. |
| Profile — avatar, editable name / weight / height, BMI, delete account | ✅ | `ui/profile/ProfileFragment.kt` + `backend/fitness-api/controllers/UserController.php`. |
| Backend (PHP + MySQL, prepared statements, per-user scoping) | ✅ | `backend/fitness-api/` entire tree. Every query goes through `PDO::prepare`. |

### Non-functional

| Requirement | Status | Where |
|---|---|---|
| Material Design 3 look | ✅ | `app/src/main/res/values/styles.xml` + `type.xml` + `colors.xml` — the Kinetic Glow design system. |
| Light mode only (per brief) | ✅ | `AppCompatDelegate.MODE_NIGHT_NO` in `FitnessApp.onCreate`. No `values-night/`. |
| Error handling | ✅ | `ApiResult<T>` sealed interface; every ViewModel branches on Success / Error / NetworkError and surfaces a Snackbar with Retry. |
| Permissions handled gracefully | ✅ | `tracking/LocationPermissions.kt` + `LocationPermissionFragment.kt`; denial routes to manual logging, "don't ask again" opens app settings. |

## Task 1b — architecture + design chapter cites

The written report's architecture and design chapters pull on:

| Point | Cite |
|---|---|
| MVVM with manual DI | `AppContainer.kt`, every `*ViewModel.factory(container)`. |
| Repository pattern + interface | `data/repository/WorkoutRepository.kt` + `RemoteWorkoutRepository.kt`. Phase-7 adds `GoalRepository`. |
| Pure-Kotlin domain layer | `data/model/*`, `tracking/TrackCalculator.kt`, `tracking/RouteGeometry.kt`, `analytics/ProgressAnalytics.kt`, `insights/InsightEngine.kt` — all JVM-testable. |
| Inheritance example #1 — Workout | `data/model/Workout.kt` + its KDoc. |
| Inheritance example #2 — BaseFragment | `ui/base/BaseFragment.kt` + its KDoc. |
| Inheritance example #3 — BaseViewHolder | `ui/base/BaseViewHolder.kt` + its KDoc. |
| Inheritance example #4 (bonus) — Goal | `data/model/Goal.kt` + its KDoc. |
| GPS filter story (sensor accuracy) | `tracking/TrackingConstants.kt` + `TrackingSession.onFix`. |
| Why no background location | Dev-log phase 8a step 2 "Key design decision". |
| Avatar re-encoding security story | `controllers/UserController.php::uploadAvatar` + `avatarImage` (JPEG stored in MySQL, served by an unguessable name). |
| Cloud deployment (CI/CD to a cloud server) | `render.yaml` + `backend/Dockerfile`; README "Option C"; dev-log phase 9 step 2. |
| Custom chart View | `ui/progress/BarChartView.kt` — ~100 line onDraw. |
| Idempotent POST /workouts | `WorkoutController::create` upsert path. |
| Insights priority order | `insights/InsightEngine.kt` class-level KDoc. |

## Testing

**264 unit tests, 0 failures** as of phase 8 step 6:

- `data/model/*Test.kt` — the domain hierarchy + goal calculator.
- `data/remote/mapper/*Test.kt` — DTO ↔ domain round-trips.
- `data/repository/*Test.kt` — Remote repositories + their fake counterparts.
- `ui/*/*Test.kt` — every ViewModel + the auth validators + formatters.
- `tracking/*Test.kt` — TrackCalculator, TrackingSession, RouteGeometry.
- `analytics/*Test.kt` — ProgressAnalytics boundary cases.
- `insights/*Test.kt` — each of the five insight rules + the ranking.

Run with:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testLocalDebugUnitTest
```

## Report screenshots

See `docs/screenshots/README.md` for the capture plan. Required
figures:

| # | Filename | Captures |
|---|---|---|
| 1 | `geolocation_1_permission_dialog.png` | OS location permission dialog |
| 2 | `geolocation_2_live_tracking_in_progress.png` | Live tracking screen mid-run |
| 3 | `geolocation_3_route_on_detail_screen.png` | Saved route on detail (replaces the pre-8c "route + splits" shot — splits removed) |
| 4 | `geolocation_4_route_points_in_phpmyadmin.png` | `route_points` table rows |
| 5 | `geolocation_5_foreground_notification.png` | Ongoing tracking notification *(optional)* |

Phase 8c step 8 adds six new figures from the logging + maps redesign.
Each sits next to its design (`docs/design/logging-v2/<matching>.dc.html`):

| # | Filename | Captures |
|---|---|---|
| 6 | `log-sheet.png` | "Log a workout" bottom sheet with the three activity tiles + context options (Track with GPS / Enter manually for distance activities). |
| 7 | `log-manual.png` | Manual entry form with dark summary card at the top — showing a non-zero pace / speed / calories after filling the fields. |
| 8 | `tracking-live.png` | Live-tracking screen with the glowing lime polyline on the dark map, GPS pill reading **GPS strong**, non-zero stats and the pause / lock / finish controls. |
| 9 | `tracking-locked.png` | Lock overlay covering the screen; "Hold to unlock" pill visible mid-progress. |
| 10 | `workout-detail.png` | Finished workout screen — self-drawn header, dark map card with activity pill + expand button + distance pill, 2 × 2 stat grid with highlighted lime pace card. |
| 11 | `workout-detail-fullmap.png` | Full-screen interactive map opened via the expand button; close pill top-right. |

Shoot the base figures on the emulator using the Hyde Park GPX fixture
at `docs/testing/sample_route.gpx`. Walkthrough is in
`docs/testing/README.md`. For the phase 8c figures, follow the
"Pending user action" walkthrough blocks in `docs/dev-log.md` phase 8c
step 6 (tracking) and step 7 (detail).

## Attributions

- **Map tiles**: © OpenStreetMap contributors
  (<https://www.openstreetmap.org/copyright>). Rendered via osmdroid's
  default MAPNIK tile source — the "© OpenStreetMap" overlay is drawn
  by `DarkMapStyler` on every map in the app (`LiveTrackingFragment`,
  `WorkoutDetailFragment`, `FullScreenMapFragment`). No API key, no
  bundled tiles.
- **Phase 8c design canvas**: the HTML sources in
  `docs/design/logging-v2/` were authored by me in a Claude design
  canvas (same workflow as phase 8b's `docs/design/goals-v2/`) and
  then re-implemented in Android XML. The HTML is kept in the repo so
  the examiner can see design and implementation side by side.
