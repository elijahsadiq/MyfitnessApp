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
| **Geolocation** | ✅ Shipped | Engine: `app/src/main/java/com/elijah/fitness/tracking/`. Service: `LocationTrackingService.kt` (foreground, `type=location`). UI: `ui/tracking/LiveTrackingFragment.kt`. Storage: `route_points` table (migration 04). Screenshots: `docs/screenshots/geolocation_*.png`. |
| **Three inheritance hierarchies** | ✅ Shipped + KDoc'd | `Workout.kt` (hierarchy #1), `BaseFragment.kt` (#2), `BaseViewHolder.kt` (#3). Phase-7 bonus: `Goal.kt` (#4). All four carry their inheritance-example KDoc for the report. |

### Core feature set

| Requirement | Status | Where |
|---|---|---|
| User registration / login | ✅ | `ui/auth/` + `data/remote/api/AuthApi.kt` + `data/repository/RemoteAuthRepository.kt`. SHA-256 token hash in `auth_tokens`. |
| Logging the three activity types | ✅ | `ui/log/LogDistanceWorkoutFragment.kt` (running + cycling), `ui/log/LogStrengthWorkoutFragment.kt`. Entry point: `ChooseActivityBottomSheet` (phase 8a offers Track-with-GPS / Enter-manually for Running+Cycling). |
| Workout history | ✅ | `ui/history/WorkoutHistoryFragment.kt` + section-headered adapter + weekly momentum chart. |
| Workout detail with route + splits | ✅ | `ui/history/WorkoutDetailFragment.kt` + `tracking/RouteSplits.kt`. |
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
| Pure-Kotlin domain layer | `data/model/*`, `tracking/TrackCalculator.kt`, `tracking/RouteSplits.kt`, `analytics/ProgressAnalytics.kt`, `insights/InsightEngine.kt` — all JVM-testable. |
| Inheritance example #1 — Workout | `data/model/Workout.kt` + its KDoc. |
| Inheritance example #2 — BaseFragment | `ui/base/BaseFragment.kt` + its KDoc. |
| Inheritance example #3 — BaseViewHolder | `ui/base/BaseViewHolder.kt` + its KDoc. |
| Inheritance example #4 (bonus) — Goal | `data/model/Goal.kt` + its KDoc. |
| GPS filter story (sensor accuracy) | `tracking/TrackingConstants.kt` + `TrackingSession.onFix`. |
| Why no background location | Dev-log phase 8a step 2 "Key design decision". |
| Avatar re-encoding security story | `controllers/UserController.php::uploadAvatar` + `uploads/avatars/.htaccess`. |
| Custom chart View | `ui/progress/BarChartView.kt` — ~100 line onDraw. |
| Idempotent POST /workouts | `WorkoutController::create` upsert path. |
| Insights priority order | `insights/InsightEngine.kt` class-level KDoc. |

## Testing

**264 unit tests, 0 failures** as of phase 8 step 6:

- `data/model/*Test.kt` — the domain hierarchy + goal calculator.
- `data/remote/mapper/*Test.kt` — DTO ↔ domain round-trips.
- `data/repository/*Test.kt` — Remote repositories + their fake counterparts.
- `ui/*/*Test.kt` — every ViewModel + the auth validators + formatters.
- `tracking/*Test.kt` — TrackCalculator, TrackingSession, RouteSplits.
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
| 3 | `geolocation_3_route_on_detail_screen.png` | Saved route + per-km splits on detail |
| 4 | `geolocation_4_route_points_in_phpmyadmin.png` | `route_points` table rows |
| 5 | `geolocation_5_foreground_notification.png` | Ongoing tracking notification *(optional)* |

Shoot these on the emulator using the Hyde Park GPX fixture at
`docs/testing/sample_route.gpx`. Walkthrough is in
`docs/testing/README.md`.
