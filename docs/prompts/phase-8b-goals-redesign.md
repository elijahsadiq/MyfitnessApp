# Phase 8b: Goals redesign, days-based goals, and app-wide font

This phase rebuilds the Goals feature to match the new designs in `docs/design/goals-v2/`, changes goals from fixed "this week / this month" periods to a chosen number of days, adds a user-selected main goal, and applies the Plus Jakarta Sans font across the entire app.

Read `CLAUDE.md` first. All existing constraints still apply:

- **Kotlin with XML Views and ViewBinding. No Jetpack Compose anywhere.**
- MVVM, with manual dependency injection through `AppContainer`.
- Coroutines and `StateFlow`.
- All styling goes through the design system: no hardcoded colours, dimensions or strings in layouts.
- The backend stays plain PHP 8 with PDO prepared statements and MySQL, with no Composer and no framework, and must keep running on XAMPP's MariaDB.

**Ordering:** if a phase 8a step is mid-flight, finish and commit it first. This phase does not depend on geolocation. It replaces amendment A (goal expiry) in `phase-8a-geolocation.md`, because the ended state is built here.

---

## Working agreement (unchanged)

- **Start with Step 0 only.** Inspect, report, propose a plan, then stop and wait for my approval before editing any files.
- One step at a time. After each step: the build passes, you give me a short summary explaining any new Kotlin or Android concept in one line, you commit with a clear message, and you wait for my "continue".
- Ask before adding any library. I don't expect this phase to need one.
- Add an entry to `docs/dev-log.md` for every step: what was built, the key design decision, one rejected alternative and why, and which assignment task it supports.
- **Show me every database migration before running it.** The database is seeded and in use.
- If anything here conflicts with the actual state of the project, stop and tell me rather than working around it.

Build commands:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## The designs

The design files are in `docs/design/goals-v2/` as `.dc.html` files. Open them in a browser to see each screen, and read the HTML for exact values. **They are a visual reference only: do not port any HTML, CSS or JavaScript into the app.** Build native XML layouts that match them.

| File | Screen |
|---|---|
| `Main.dc.html` | Goals list: main goal card, active goals, completed, ended |
| `NewGoal.dc.html` | New goal (full screen), also used for editing |
| `Manage.dc.html` | Manage goal bottom sheet |
| `DeleteConfirm.dc.html` | Delete confirmation dialog |
| `Empty.dc.html` | Goals tab with no goals yet |

`NewGoal.dc.html` contains working JavaScript. Its `renderVals()` function is the clearest statement of the form's rules (which tracking options each activity allows, step sizes, limits, how the summary sentence is built), so read it as a specification, then implement the same rules in Kotlin.

Design tokens used throughout (these should already match the theme; flag any that don't):

| Token | Value |
|---|---|
| Background | `#F5F6F2` |
| Card | `#FFFFFF`, 1dp border `#E4E6E0` |
| Dark card | `#1C1F1A`, inner tiles `#2A2E27` |
| Selected tile / pill | `#121411` / `#111410` with white text |
| Primary text | `#111410` |
| Secondary text | `#6B7066` |
| Muted text on dark | `#B4B9AE` |
| Accent | `#D4F26A` (text on it always `#111410`) |
| Progress fill on light | `#C6E85A`, track `#ECEEE7` |
| Icon tile | `#F1F3EC` |
| Destructive | `#B42318` |
| Radii | main card 26dp, cards 20dp, sheets 28dp top, icon tiles 14dp, pills fully rounded |

---

## Step 0: Inventory and plan (read-only)

Read the design files, `CLAUDE.md`, `docs/dev-log.md` and the current goals code on both sides, then report:

1. **The current `goals` table** exactly as it exists in the database and in `backend/sql/`, and every `/goals` endpoint with its request and response shape.
2. **The current `Goal` domain hierarchy**: how `GoalPeriod`, `periodStart` and `periodEnd` are used, and every place in the app that depends on them.
3. **The current font setup.** The app was meant to use Plus Jakarta Sans from phase 2, but the screens I'm seeing look like the system default. Tell me exactly what exists: `res/font/` contents, the theme's font attributes, which text appearances set a font, and whether downloadable fonts are configured. Identify why it isn't showing.
4. **Every component that renders text** and needs checking for the font: toolbar titles, bottom navigation labels, buttons, chips, text fields and their hints, Snackbars, dialogs, bottom sheets, tabs, and any custom views.
5. **A proposed migration** for the `goals` table (see Step 2), shown in full, with how existing week and month goals will be converted.
6. A numbered plan for Steps 1 to 9 with the files you expect to create or change.

**Then stop and wait for my approval.**

---

## Step 1: Plus Jakarta Sans across the whole app

The design uses **Plus Jakarta Sans** everywhere, in weights 400, 500, 600, 700 and 800. Every piece of text in the app must use it, including system components.

- **Bundle the font files in the app rather than using downloadable fonts.** Downloadable fonts depend on Google Play Services fetching the font on first use, so the app could render in the default font on the marker's emulator if that fetch fails. Bundled files always work. Note this decision in the dev log.
- I will place the static TTF files from Google Fonts in `docs/design/fonts/`. Copy the five weights into `res/font/` with valid resource names (`plus_jakarta_sans_regular.ttf`, `_medium`, `_semibold`, `_bold`, `_extrabold`), and create a `res/font/plus_jakarta_sans.xml` font family that maps each `android:fontWeight` (and the matching `app:` attributes) to its file. If the files are not there when you reach this step, stop and tell me.
- The font is licensed under the SIL Open Font License, so copy its `OFL.txt` into `res/font/` alongside it or into `docs/licenses/`, and mention it in the README.
- Apply it at the **theme level**, not per view:
  - set `android:fontFamily` and `fontFamily` on the app theme
  - override every Material 3 type scale attribute the app uses (`textAppearanceDisplay*`, `textAppearanceHeadline*`, `textAppearanceTitle*`, `textAppearanceBody*`, `textAppearanceLabel*`) with styles that use the family and the right weight
  - update the existing custom text appearances (screen title, section heading, body, caption, metric) to use the family, with the metric style at weight 800
  - make sure `BottomNavigationView` active and inactive label appearances, `MaterialToolbar` title, `MaterialButton`, `Chip`, `TextInputLayout`, `Snackbar`, `MaterialAlertDialog` and bottom sheet text all pick it up
- **Remove any per-view `android:fontFamily` or `android:textStyle="bold"` that fights the theme**, replacing bold with the correct text appearance so weights come from the font files rather than synthetic bolding.
- Verify on every screen, light and dark, by taking a screenshot of each one. List any text you found still using the default font and what you changed.

---

## Step 2: Backend changes

### Migration

Goals now cover a chosen number of days starting on the day they are created, instead of a fixed week or month. Propose the migration in Step 0 and run it only after I approve it.

- Add `duration_days` (`SMALLINT UNSIGNED NOT NULL`, 1 to 365).
- Keep `period_start` as the goal's start date, and store `period_end` as the **last day included** in the goal (`start + duration_days - 1`). If the current column types make this awkward, propose renaming them to `start_date` and `last_day` as `DATE` columns.
- Remove the `period` week/month column once its data has been converted.
- Add `is_main` (`TINYINT(1) NOT NULL DEFAULT 0`).
- Convert existing rows: a week goal becomes 7 days from its start date, a month goal becomes the number of days in that month. Show me the conversion SQL and the before-and-after row counts.
- Keep everything MariaDB compatible (`utf8mb4_unicode_ci`, no MySQL 8 only syntax), and update `backend/sql/01_schema.sql` and `02_seed.sql` to match, so a fresh install gets the new shape. The seed should include one main goal, two other active goals, one completed goal and one ended goal, so the Goals screen is fully populated on first run.

### Endpoints

Follow every existing convention: prepared statements, the standard JSON envelope, per-user scoping and server-side validation.

| Method | Path | Change |
|---|---|---|
| GET | `/goals` | returns `durationDays`, `startDate`, `lastDay` and `isMain` instead of the period fields |
| POST | `/goals` | accepts `activityType`, `goalType`, `targetValue`, `durationDays`; the server sets the start date to today and computes the last day |
| PUT | `/goals/{id}` | can change title, activity, goal type, target and `durationDays` (the start date never changes) |
| POST | `/goals/{id}/main` | makes this goal the main goal, clearing `is_main` on the user's other goals in the **same transaction** |
| POST | `/goals/{id}/extend` | body `{"days": 7}`; adds days to an active goal, up to the 365 day maximum |
| DELETE | `/goals/{id}` | unchanged |

Validation, on the server as well as the client:

- `durationDays` between 1 and 365, and an extension may not push the total past 365.
- `targetValue` above 0 and within the limits in Step 6.
- Goal types allowed per activity: running and cycling allow distance, time and sessions; workout allows time, sessions and weight; any activity allows time and sessions. Reject anything else with a field error.
- Extending an ended goal is rejected with a clear message. Ended goals are restarted with "Try again", which creates a new goal.
- At most one main goal per user. Enforce it in the transaction, since MySQL cannot express "one row per user where is_main = 1" as a constraint.

**Dates and time zones:** the server stores dates, not timestamps, for goals. The client sends its local date when creating a goal (`startDate`), and the server validates that it is within one day of the server's date, so a user in Kuwait creating a goal just after midnight gets the right start day. Explain this in the dev log.

Add all new and changed requests to `backend/api-tests.http`, including a rejected 400 day goal, a rejected weight goal for running, and a rejected extension of an ended goal.

---

## Step 3: Domain model

Update the `Goal` hierarchy **without breaking it**. It is one of the inheritance examples in my report, so the structure (filtering on the base class, measuring in each subclass) must stay exactly as it is.

- Replace `GoalPeriod` with `startDate: LocalDate` and `durationDays: Int`, and derive `lastDay` from them.
- `relevantWorkouts()` on the base class filters to workouts from `startDate` through `lastDay` inclusive, in the device's time zone, plus the activity filter.
- Add a goal **status**, worked out from the date and progress:
  - **Active:** today is within the goal's days and the target isn't met yet.
  - **Completed:** the target was met (show the date it was met, and keep it completed even if the goal's days have since passed).
  - **Ended:** the last day has passed without the target being met.
- **Days left counts today:** `lastDay - today + 1`. Show "Last day" when it is 1. This must agree everywhere the app shows it.
- Add the calculations the Manage sheet needs, on the base class so every goal type gets them:
  - **average per day so far**: current value divided by days elapsed, counting today, minimum 1
  - **needed per day**: remaining value divided by days left, or zero once complete
  - **sessions logged**: the number of matching workouts in the goal's days
- **Unit tests**, covering at least:
  - status on the first day, a middle day, the last day, and the day after the last day
  - a goal completed early staying completed after its last day
  - days left, including "Last day"
  - average per day and needed per day, including the first day (no division by zero)
  - a workout at 23:59 on the last day counting, and one at 00:00 the next day not counting
  - the existing per-subclass measurement tests, updated and still passing

---

## Step 4: Data layer

- Update the DTOs, mappers and `GoalApi` for the new fields and the two new endpoints.
- `GoalRepository` gains `makeMainGoal(id)` and `extendGoal(id, days)`.
- `GoalProgressCalculator` produces everything the screens need per goal: status, current value, target, fraction, days left, the three Manage statistics, and whether it is the main goal.
- **Which goal goes in the dark card:** the main goal if it is active. Otherwise the active goal with the fewest days left. If there are no active goals, show no dark card at all. Never feature a completed or ended goal there.
- Undo for delete: hide the goal immediately, show a Snackbar with Undo for about five seconds, and only call `DELETE` when the Snackbar closes without Undo being pressed. Say in the dev log why this approach was chosen over deleting and then recreating.

---

## Step 5: Goals list screen (`Main.dc.html`)

- Header: a small uppercase caption with the counts ("3 active · 1 completed"), the "Goals" title at 34sp weight 800, and the lime "+ New goal" pill.
- **Main goal card** (dark): a pill with the activity icon and "Main goal · Running" (or just the activity name when the card is showing the fallback goal rather than a chosen main goal), a "12 days left" pill, the goal title, the large current value with "/ 50 km" beside it, and the lime progress ring with the percentage and "done". The whole card opens the Manage sheet.
- **Active goals:** a RecyclerView. Each row has the icon tile, the title, a second line with **only** the progress ("58 / 100 km"), the days-left pill, and the progress bar. Tapping a row opens the Manage sheet. Extend the existing `BaseViewHolder`.
- **Completed:** a compact row with "Finished Fri 2 Oct · 46 km" and the lime check badge.
- **Ended:** a muted row with a dashed border, "Ended Mon 28 Sep · 16 of 20 km", and an outlined "Try again" button that opens New goal pre-filled with the same activity, type, target and number of days.
- Sections with no goals in them are hidden entirely, including their headers.
- All sections live in one scrolling RecyclerView with multiple view types (header, main card, active row, completed row, ended row), rather than nested scroll views. Each view type has its own view holder extending `BaseViewHolder`, which strengthens the inheritance write-up.
- Loading and error states as before.

---

## Step 6: New goal and Edit goal screen (`NewGoal.dc.html`)

A full-screen destination, not a bottom sheet, with a close button top left and the title "New goal" (or "Edit goal").

- **Choose an activity:** a 2 by 2 grid of tiles (Running, Cycling, Workout, Any activity). The selected tile is dark with a lime icon tile. Use real buttons with a selected state readable by TalkBack.
- **What do you want to track?** A hint line that changes with the activity, then the allowed tracking options **in a single row of equal-width buttons**, each showing the name on top and the unit underneath:
  - Running: Distance (km), Time (min), Sessions (runs)
  - Cycling: Distance (km), Time (min), Sessions (rides)
  - Workout: Time (min), Sessions (workouts), Weight (kg)
  - Any activity: Time (min), Sessions (sessions), as two half-width buttons
  - They must stay on one row at 320dp width and at the largest system font size. Use a horizontal chain with equal weights and let the text shrink or wrap inside each button rather than wrapping the buttons to a second row.
  - Changing activity keeps the tracking option if the new activity allows it; otherwise it switches to the first allowed option and resets the target to that option's default.
- **Set your target:** a large number with its unit between a minus button and a plus button.

  | Type | Step | Min | Max | Default |
  |---|---|---|---|---|
  | Distance | 5 km | 1 | 1,000 | 50 |
  | Time | 15 min | 15 | 10,000 | 150 |
  | Sessions | 1 | 1 | 100 | 4 |
  | Weight | 1,000 kg | 1,000 | 500,000 | 10,000 |

- **How many days?** The same stepper, 1 to 365, default 30, with "Starts today · ends {last day}" beneath it. Show the **last day included** (today + days - 1), not today + days.
- **There are no preset buttons for either number.** I removed them on purpose. Because of that, both steppers must make large values practical:
  - **holding** the minus or plus button repeats, speeding up the longer it is held
  - **tapping the number** opens a small dialog with a numeric keyboard to type the value directly, validated against the same limits
- **Summary card** (dark): "Your goal", the generated sentence (for example "Ride 100 km in 30 days", "Train for 150 min in 7 days", "4 runs in 14 days", "Lift 10,000 kg in 30 days"), and two tiles: "Per day" (or "Per week" for sessions) and "Deadline". Build the sentence from string resources with placeholders and plurals, never by concatenation, so it handles "1 day" and "1 run" correctly.
- **"Create goal"** is a full-width lime pill pinned to the bottom, above the gesture bar. It shows a loading state while saving and returns to the Goals list on success.
- **Editing** reuses this screen pre-filled, with the title "Edit goal" and the button "Save changes". Changing the number of days on an existing goal keeps its original start date.
- Server validation errors are shown inline, next to the matching section.

---

## Step 7: Manage sheet, delete and undo (`Manage.dc.html`, `DeleteConfirm.dc.html`)

**Manage bottom sheet**, opened from any active goal or the main card:

- The icon tile, the goal title, a line such as "Cycling · Distance · started Thu 24 Sep", and a close button.
- Progress: the current value large, "of 100 km", the percentage on the right, and the progress bar.
- Three statistic tiles: **Avg per day**, **Needed per day**, and **Runs / Rides / Workouts / Sessions logged**, matching the activity.
- Actions, in a single bordered list:
  - **Edit goal:** opens Step 6 in edit mode.
  - **Make main goal:** hidden when this goal is already the main goal. Moves it to the dark card immediately.
  - **Add 7 more days:** hidden when the goal is already at 365 days. Updates the days left immediately.
  - **Delete goal:** in the destructive colour, opens the confirmation dialog.

**Delete confirmation dialog:** the trash icon in a pale red circle, "Delete this goal?", a sentence naming the goal and confirming that the workouts themselves stay in History (use the activity word: "Your rides stay in History"), and Cancel / Delete buttons. Delete closes the dialog and the sheet, removes the goal from the list, and shows the Undo Snackbar from Step 4.

Completed and ended goals are not opened in this sheet. Ended goals have "Try again", and completed goals are display only.

---

## Step 8: Empty state (`Empty.dc.html`)

When the user has no goals at all:

- The header with "No goals yet" as the caption and the "+ New goal" pill.
- A dark card with a lime target icon tile, "Set your first goal", a one-sentence explanation, and a full-width "Create a goal" button.
- A "Quick start" list with three suggestions, each opening New goal pre-filled: "Run 20 km in 14 days", "Ride 100 km in 30 days", and "Train 150 min in 7 days", each with its per-day figure underneath.

---

## Step 9: Verification and report evidence

- Walk the full flow on the emulator: create a goal of each tracking type, edit one, make one the main goal, extend one, delete one and undo it, delete one for real, and log a workout to confirm progress moves.
- Check the Goals screens at 320dp width, on a tablet, in landscape, at the largest system font size, and in dark mode. The tracking options must stay in one row in every case.
- Confirm in phpMyAdmin that `is_main` is set on only one goal per user, and that a new goal's `period_start` and last day are correct.
- Capture screenshots into `docs/screenshots/` of each Goals screen next to its design, named so I can use them as figures (`goals-list.png`, `goals-new.png`, `goals-manage.png`, `goals-delete.png`, `goals-empty.png`).
- Add a short note to `docs/dev-log.md` recording that the designs were created with Claude in a design canvas, so I can acknowledge it in the report.

---

## Definition of done

- [ ] `assembleDebug` and `testDebugUnitTest` pass
- [ ] Plus Jakarta Sans is bundled and used by every piece of text in the app, including system components, in light and dark mode
- [ ] The goals migration has run, existing goals were converted, and a fresh install from `backend/sql/` produces the new shape with a full seed
- [ ] Goals are created with a number of days from 1 to 365, and the last day shown is the last day included
- [ ] One main goal per user, chosen in the Manage sheet, shown in the dark card
- [ ] Active, completed and ended states are correct on the first day, last day and the day after
- [ ] The tracking options stay in one row at 320dp and the largest font size
- [ ] Steppers support hold-to-repeat and typing a value
- [ ] Delete has a confirmation dialog and a working Undo
- [ ] The `Goal` inheritance hierarchy is intact, with updated KDoc and passing tests
- [ ] No hardcoded colours, dimensions or strings in any new or changed layout
- [ ] Screenshots captured, and `docs/dev-log.md` has an entry per step

## Out of scope
Recurring goals, shared or social goals, goal notifications beyond what already exists, changes to the Home screen, and changes to the Progress screen.

## Final report
1. A short summary of what changed.
2. The final `goals` table and the migration that was run.
3. The new and changed endpoints with example requests and responses.
4. The `Goal` hierarchy as a text diagram, noting what changed and what stayed the same.
5. Any screen that doesn't match its design exactly, and why.
