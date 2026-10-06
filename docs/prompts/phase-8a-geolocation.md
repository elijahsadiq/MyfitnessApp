# Phase 8a: Geolocation (do this before the rest of phase 8)

The phase 8 audit found geolocation missing. The assignment names it explicitly as a required component of Task 4, which carries 40 of the 100 marks, so it outranks every cosmetic item in phase 8. Build this first, then return to `phase-8-polish-and-completion.md` and apply the amendments at the end of this file.

Read `CLAUDE.md` first. All existing constraints still apply:

- **Kotlin with XML Views and ViewBinding. No Jetpack Compose anywhere.**
- MVVM, with manual dependency injection through `AppContainer`.
- Coroutines and `StateFlow`.
- All styling goes through the existing design system: no hardcoded colours, dimensions or strings in layouts.
- The backend stays plain PHP 8 with PDO prepared statements and MySQL, with no Composer and no framework.

---

## Working agreement (unchanged)

- **Start with Step 0 only.** Inspect, report, propose a plan, then stop and wait for my approval before editing any files.
- One step at a time. After each step: the build passes, you give me a short summary explaining any new concept in one line, you commit, and you wait for my "continue".
- Ask before adding any library. This phase expects one request, for route display. See Step 0.
- Add an entry to `docs/dev-log.md` for every step.
- If anything here conflicts with the project, stop and tell me.

Build commands:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## Step 0: Confirm and plan (read-only)

The audit already established that geolocation is absent and that `LocationPermissionFragment` is priming only. Confirm the following and then propose the plan:

1. `minSdk`, `targetSdk` and `compileSdk`, since the foreground service and notification rules differ by API level.
2. Whether any route, GPS or track table exists in `backend/sql/01_schema.sql`. The audit listed `users`, `auth_tokens`, `workouts`, `strength_sets` and `goals`, so a route table almost certainly needs creating.
3. What the Home "Start workout" button currently does, and how `ChooseActivityBottomSheet` routes to the log forms. Live tracking needs to slot into that flow without breaking manual entry.
4. Whether `POST /workouts` could accept route points without breaking its current contract.

### The route display decision, which I need you to put to me in your report

Google Maps on Android needs an API key tied to the signing certificate. If I submit this project with a key restricted to my debug certificate, **the map will not render on the marker's machine**, and the brief says media that cannot be run directly may be awarded a fail. Shipping an unrestricted key in a repository is worse.

Propose one of these, with the trade-offs, and wait for my decision:

- **osmdroid** (OpenStreetMap tiles). No API key, no Google Play Services dependency for the map itself, renders on any machine. My preferred option unless you see a problem with it.
- **A custom `View` that draws the route on a `Canvas`** from the stored points, with no map tiles at all. Zero dependencies and guaranteed to run anywhere, and a custom view is a strong thing to write about in the report. Weaker visually, since there is no street context.
- **Google Maps SDK**, only if you can show me a key handling approach that still works on a machine that is not mine.

Note that `FusedLocationProviderClient` comes from Google Play Services location, which is separate from the map and is fine to use either way, because the emulator and real devices both have it.

**Then stop and wait for my approval.**

---

## Step 1: Backend route storage

- Add a `route_points` table:
  - `id`, `workout_id` (FK to `workouts`, cascade delete), `seq` (point order within the workout), `latitude` `DECIMAL(9,6)`, `longitude` `DECIMAL(9,6)`, `elapsed_ms`, `accuracy_m`, `recorded_at`
  - Index on `(workout_id, seq)`
  - `utf8mb4_unicode_ci`, InnoDB, no MySQL 8 only syntax, because the marker will run this on MariaDB under XAMPP
- The database is already seeded, so **show me the migration before running it**.
- Extend `POST /workouts` to accept an optional `routePoints` array, inserted inside the same transaction as the workout. Keep the endpoint backwards compatible: a request with no route points must behave exactly as it does today.
- Extend `GET /workouts/{id}` to return the route points in sequence order.
- Validate server side: latitude between -90 and 90, longitude between -180 and 180, `seq` unique within a workout, and a cap of 10,000 points per workout so a malformed client cannot flood the table. Reject with a per-field error.
- Use a prepared statement executed in a loop, or a batched multi-row insert, never string concatenation.
- Add the new cases to `backend/api-tests.http`, including one rejected out-of-range coordinate.

---

## Step 2: Permissions

- Declare in the manifest: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS`.
- **Do not request background location.** Tracking happens while the workout is active with a foreground service, which does not need it, and requesting it triggers a far stricter Play review. Say this in the dev log, because it is a good design justification for the written report.
- Wire the existing `LocationPermissionFragment` to the real runtime request, replacing its TODO.
- Handle every outcome properly:
  - **Granted fine:** proceed to tracking.
  - **Granted coarse only:** explain that accurate distance needs precise location, and offer either to continue with manual entry or to open settings.
  - **Denied:** the app must stay fully usable. Manual logging already works, so route the user there rather than blocking them.
  - **Denied permanently:** show a rationale with a button that opens the app's settings page, since the system dialog no longer appears.
- On Android 13 and above, request `POST_NOTIFICATIONS` as well, because the foreground service notification is how tracking stays visible. Treat a refusal as non-fatal: tracking still runs.

---

## Step 3: Location engine and track calculations

- A `LocationTracker` wrapping `FusedLocationProviderClient` with `Priority.PRIORITY_HIGH_ACCURACY` and an update interval of roughly 2 to 3 seconds. Expose updates as a `Flow`.
- **Point filtering**, which is where the quality marks are. Discard a point when:
  - its accuracy is worse than a threshold (start at 25 metres)
  - the implied speed from the previous point is implausible for the activity, which filters GPS jumps
  - it is closer than about 5 metres to the previous point, which stops a stationary device accumulating drift
  Keep every threshold in one named constants object with a comment, and explain the filtering in the dev log. Sensor accuracy is a discussion point in Task 1b of the written report, and this gives me a concrete example to cite.
- A `TrackCalculator` in pure Kotlin with no Android imports, computing total distance, moving time, current and average pace, and current speed from a list of points. **Unit test it** against a synthetic point list with known distances, including a case with a rejected outlier point and a case with a pause in the middle.
- A `TrackingState` data class exposed as `StateFlow`: elapsed time, distance, current pace, average pace, point count, GPS quality and whether tracking is paused.
- Distance uses `Location.distanceBetween` or a Haversine implementation. If you write Haversine yourself, test it against two known coordinates.

---

## Step 4: Foreground service

- A `LocationTrackingService` declared with `android:foregroundServiceType="location"`.
- A notification channel and an ongoing notification showing elapsed time and distance, which returns to the tracking screen when tapped. No dismissal while tracking.
- Commands for start, pause, resume and stop, with the service owning the authoritative tracking state so the screen can be rebuilt from it after a rotation.
- Verify tracking continues with the screen off and the app in the background.
- Stop cleanly: remove location updates, cancel the notification, release the wake lock if you take one, and leave no leaked callbacks. Check with a rotation and a back press.
- Document in the dev log what happens if the process is killed mid-workout, even if the answer for now is that the workout is lost. Being explicit about a limitation is better than pretending it cannot happen.

---

## Step 5: Live tracking screen

There is no Stitch export for this screen, so build it from the design system and the original design description: dark immersive on the near-black surface, no bottom navigation.

- Elapsed time as the largest element, then distance, current pace and estimated calories.
- A GPS quality pill at the top, driven by real accuracy values, showing something like "GPS strong", "GPS weak" or "Searching".
- Route display above the stats, using whatever we chose in Step 0, updating as points arrive.
- Controls: a large circular pause and resume, a stop, and a screen lock that blocks accidental taps.
- Keep the screen on while tracking.
- Stopping asks for confirmation, then goes to a save step where the user can add notes before the workout is saved with its route points.
- If saving fails, keep the tracked data on screen with a retry. There is no offline cache in the project, so **never discard a completed workout because a network call failed**, and note this limitation in the dev log.
- Reached from the activity chooser for running and cycling, which must now offer a choice between tracking with GPS and entering manually. Weightlifting is unaffected.

---

## Step 6: Route on the workout detail screen

- The workout detail screen shows the saved route for GPS workouts, fetched with the workout.
- Per-kilometre splits with their pace, using the existing design's thin bars.
- A workout with no route points shows its stats as it does today, with no empty map container.

---

## Step 7: Verification and report evidence

- Test with the emulator's extended controls, which can play back a GPX route. Save a GPX file into `docs/testing/` so this is repeatable and so I can demonstrate it without going outside.
- Walk the full path: permission request, tracking with the screen off, pause and resume, stop, save, and the route reappearing on the detail screen and in the `route_points` table in phpMyAdmin.
- Test the denial path: refuse the permission and confirm manual logging still works everywhere.
- Capture screenshots into `docs/screenshots/` showing the permission dialog, live tracking in progress, the saved route on the detail screen, and the `route_points` rows in phpMyAdmin. Name them so I can reference them as figures in the report.

---

## Definition of done for phase 8a

- [ ] `assembleDebug` and `testDebugUnitTest` pass
- [ ] A run or ride can be tracked end to end and saved with its route
- [ ] Tracking continues with the screen off, and survives a rotation
- [ ] Route points are visible in the `route_points` table and redisplay on the detail screen
- [ ] Refusing the location permission leaves the app fully usable
- [ ] Point filtering is implemented, documented and unit tested
- [ ] Route display needs no API key, or we have explicitly agreed otherwise
- [ ] Screenshots captured for the report
- [ ] `docs/dev-log.md` has an entry per step

---

# Amendments to phase 8

Apply these when returning to `phase-8-polish-and-completion.md`. The audit surfaced them, or they correct something I got wrong.

### A. Goal expiry, which is the real bug behind the "Last day" defect

The audit diagnosed the days-remaining calculation correctly, but there is a bigger problem underneath it. A goal created last Tuesday with period "this week" stores a `periodEnd` of last Sunday. That goal has **expired**, yet it still appears under Active goals and was selected for the featured card. The calculation fix alone would simply show it as a negative number.

So, in addition to the calendar-day fix the audit proposed:

- Add a third goal state alongside active and completed: **ended**, meaning the period has passed and the target was not met.
- Active goals are only those whose period contains today. Ended goals move to their own section, below Completed, with the final value shown, for example "32 of 50 km". They are not deleted, since a user may want to see them.
- **The featured card only ever selects from goals whose period is current and incomplete.** If there are none, show either a completed goal or the empty state, never an expired one.
- The "1 in progress" count excludes ended goals.
- Unit tests must cover the first day, a middle day and the last day of both a week and a month period, plus a goal whose period ended yesterday.

Phase 7 deliberately excluded auto-recurring goals, so expiry is the correct behaviour. Note this in the dev log, because an examiner may well ask why a weekly goal does not roll over.

### B. Profile is showing fake data

Audit defect 9 is more serious than a cosmetic issue. `ProfileFragment` never calls `GET /me` and renders `profile_default_name` and `profile_default_email` from `strings.xml`, so **a marker who registers a new account will see "Alex Runner" and "alex@example.com" on their own profile**. When rebuilding Profile:

- Bind every field to `GET /me`.
- Delete the `profile_default_name` and `profile_default_email` string resources entirely, so nothing can fall back to them.
- Sweep the whole app for other placeholder user data baked into layouts or strings, and report anything else you find.

### C. The streak pill shows nothing in release

Audit defect 12: the pill has `tools:text="4-day streak"` and no runtime binding, so it is invisible in a real build. Either compute the streak from the workout history and bind it, or remove the pill. Do not leave a tools-only element in a submission. Computing it is not hard and makes the header look finished, so prefer that unless it complicates the dashboard state.

### D. Height validation range

Ignore the 50 to 250 cm range I specified. The server already validates 80 to 260 in `UserController.php`, which is sensible for this app. **Keep the server's range and make the client match it**, so the two never disagree. The same applies to any other range where the server already has a considered value.

### E. Two smaller audit findings to fold in

- Defect 11: `goalsEmptyState` has `layout_marginBottom="@dimen/bottom_nav_height"` inside a fragment that already sits above the bottom navigation, so the height is counted twice and the call to action floats too high. Fix it as part of the Step 1 spacing pass, and check for the same mistake on other screens.
- Defect 14: several non-decorative icons carry `contentDescription="@null"`. Fix these in the accessibility pass, and reserve `@null` for genuinely decorative images only.

### F. Revised step order for phase 8

Phase 8a replaces phase 8's Step 6, so when you return to phase 8, run it as:

1. Responsive layout and spacing pass (old Step 1, now including amendments A and E)
2. Home screen fixes (old Step 2)
3. Profile backend (old Step 3)
4. Profile screen rebuild (old Step 4, now including amendment B)
5. Progress and analytics screen (old Step 5)
6. Personalised insights (old Step 7)
7. Submission readiness (old Step 8), with geolocation now marked complete in `docs/assignment-coverage.md`
