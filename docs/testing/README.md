# Testing fixtures

## `sample_route.gpx` — phase 8a geolocation verification

A short Hyde Park loop (~2.4 km / 15 min). Replays through the Android
emulator's Extended Controls so the GPS tracking flow can be demonstrated
end-to-end without stepping outside.

### How to use it

1. Boot an Android emulator (API 29 or newer — `foregroundServiceType="location"` is required there).
2. Install the debug build:
   ```
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew installLocalDebug
   ```
3. Register a new account (or log in as `alex@example.com` / `Password123` on the seeded DB).
4. In the running emulator, click the **…** button → **Location** → **Routes** tab.
5. Import `docs/testing/sample_route.gpx`.
6. Set playback speed to 1× (default) or 5× for a quicker demo.
7. In the app: Home → **Start workout** → **Running** → **Track with GPS**.
8. Grant location permission when prompted. The map centres on London
   until the first fix lands; after that it follows the route.
9. Press **Play** on the emulator's route panel.
10. Watch the live metrics tick, pause/resume mid-run if desired, hit **Stop** → confirm → add a note → **Save workout**.
11. The Snackbar confirms the save; opening the row from the History tab shows the stored route on the detail screen.

### Verifying denial path

- Boot with the app's location permission revoked (Settings → Apps → Fitness → Permissions → Location → Deny).
- Open **Start workout** → **Running** → **Track with GPS** → the OS dialog appears again.
- Deny. The onboarding screen's rationale message surfaces as a Snackbar; manual logging via **Enter manually** must stay fully functional.

### Verifying the server round-trip

After step 11, open phpMyAdmin (http://localhost:8081, user `root`, password `root`) and inspect the `fitness_app.route_points` table — the rows should match the GPX stride, with sub-metre precision in `latitude` / `longitude` and increasing `seq` + `elapsed_ms`.
