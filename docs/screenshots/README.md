# Report screenshots — phase 8a geolocation

Capture these for the written report's figures. The naming pattern
`geolocation_<n>_<slug>.png` is used elsewhere in the project so the
assignment-coverage table can reference them as figure IDs.

| # | File | Captures | Where to shoot |
|---|---|---|---|
| 1 | `geolocation_1_permission_dialog.png` | OS permission dialog firing on first tap of **Track with GPS** | Fresh install, first time entering the live-tracking screen |
| 2 | `geolocation_2_live_tracking_in_progress.png` | Live-tracking screen with the GPS pill in **Strong**, the lime polyline drawn on the map, and non-zero stats | Mid-playback of `docs/testing/sample_route.gpx`, roughly minute 8 of the loop |
| 3 | `geolocation_3_route_on_detail_screen.png` | Saved route on the workout-detail screen with the per-km splits visible underneath | Open the just-saved workout from the History tab |
| 4 | `geolocation_4_route_points_in_phpmyadmin.png` | `route_points` table rows in phpMyAdmin, sorted by `(workout_id, seq)` | http://localhost:8081 → `fitness_app` → `route_points` → **Browse** |
| 5 | `geolocation_5_foreground_notification.png` *(optional)* | Pulled-down status bar showing the "Run in progress · MM:SS · 1.23 km" notification | During step 2 above, pull down the status bar |

### Quick capture commands

Device screenshot via adb:

```
adb exec-out screencap -p > ~/Desktop/geolocation_<n>_<slug>.png
```

Or the emulator toolbar camera icon → saves to `~/Desktop/`.

### What NOT to include

- Personal information in the app bar (log in as the seed `alex@example.com` user, which the report lists as its demo credential).
- Developer-mode indicators (status-bar overlays, show-touches dots).
