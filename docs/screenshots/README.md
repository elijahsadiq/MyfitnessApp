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

---

# Report screenshots — phase 8b goals redesign

Capture these for the Goals-section figures. Each file should sit **next
to its design** (`docs/design/goals-v2/<matching>.dc.html`) so the
report can show the design + implementation side by side.

| # | File | Captures | Where to shoot |
|---|---|---|---|
| 1 | `goals-list.png` | Goals screen with real data: header eyebrow ("3 active · 1 completed"), main-goal dark card, 2+ active rows, Completed section, Ended section with "Try again" dashed row | Log in as seed `alex@example.com` (the Phase-8b seed ships five goals that cover every state) |
| 2 | `goals-new.png` | New Goal screen with a non-default state so every stepper + the summary card are visible | Tap "+ New goal" → pick Workout → Weight → bump target to 20,000 kg → set days to 45 |
| 3 | `goals-manage.png` | Manage sheet opened from an active goal with real progress + the four actions visible (Make-main shown or hidden depending on the chosen row) | Tap the "Ride 100 km in 30 days" row on the Goals screen |
| 4 | `goals-delete.png` | Delete confirmation dialog with the body copy showing the activity word ("Your rides stay in History") | From the Manage sheet, tap "Delete goal" |
| 5 | `goals-empty.png` | Empty-state hero card + three Quick Start rows | Delete every goal (and let the Undo timer expire) or use a fresh user |

### phpMyAdmin verification (goals table)

Open http://localhost:8081 → `fitness_app` → `goals` → **Browse** and confirm:

- Exactly one row per user has `is_main = 1`.
- `start_date` on a newly-created goal matches the client-side today.
- `last_day` = `start_date + duration_days - 1`.
- `duration_days` sits inside `[1, 365]`.

### Device matrix

The brief asks that the Goals screens hold up at:

- 320dp width (nexus_s or similar narrow emulator profile)
- Tablet (any 7"+ profile)
- Landscape (rotate on any phone profile)
- Largest system font size (Settings → Display → Font size → max)
- Dark mode is intentionally not supported — the app is light-only
  per the design system — but the layouts must not break if the
  system is in dark mode

For each configuration, confirm the New Goal screen's tracking-option
row stays on one line.
