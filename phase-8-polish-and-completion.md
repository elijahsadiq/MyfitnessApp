# Phase 8: Polish, profile, analytics and assignment completion

This phase does three things:

1. Fixes the layout, spacing and responsiveness problems across the app.
2. Rebuilds the Profile screen into a real account management screen.
3. Closes the remaining gaps against the NCC assignment brief, so the app covers everything Task 4 is marked on.

Read `CLAUDE.md` first. All existing constraints still apply:

- **Kotlin with XML Views and ViewBinding. No Jetpack Compose anywhere.**
- MVVM, with manual dependency injection through `AppContainer`.
- Coroutines and `StateFlow`.
- All styling goes through the existing design system: no hardcoded colours, dimensions or strings in layouts.
- The backend stays plain PHP 8 with PDO prepared statements and MySQL, with no Composer and no framework.

---

## Working agreement (unchanged)

- **Start with Step 0 only.** Inspect the project, report back and propose a plan. Then stop and wait for my approval before editing any files.
- Work one step at a time. After each step:
  - the build passes
  - you give me a short summary, explaining any new Kotlin or Android concept in one line
  - you commit with a clear message
  - you wait for my "continue"
- Ask before adding any library. This phase expects two requests (an image loading library and an image cropping or picking helper). Propose them with a one-line justification and wait.
- Add an entry to `docs/dev-log.md` for every step covering what was built, the key design decision, one rejected alternative and why, and which assignment task it supports.
- If anything here conflicts with the actual state of the project, **stop and tell me** rather than working around it.

Build commands:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## Step 0: Inventory, assignment audit and plan (read-only)

Before proposing anything, inspect the project and report back on all four of the following.

### 0a. Assignment coverage audit

The assignment names three things the app **must visibly use**: RecyclerView, FloatingActionButton and **geolocation**. Check each one and state plainly whether it is built, partly built or missing:

- **Geolocation.** Is `FusedLocationProviderClient` (or equivalent) actually used? Is there a live tracking screen, a foreground service, runtime permission handling, route points saved to the database, and a map or route display? If any part is missing, say so clearly, because this is the highest-value gap in the whole project.
- **RecyclerView.** Where is it used, and is it obvious on screen?
- **FloatingActionButton.** Where is it, and is it obvious on screen?
- **Statistics and analytics per activity.** The brief requires detailed statistics for each activity and progress tracked over time. The Progress tab is currently a placeholder, so confirm what analytics exist anywhere in the app.
- **The three inheritance hierarchies** required for the written report: confirm they still exist and still carry their KDoc.

### 0b. Current state

- What the Progress tab shows today.
- What the Profile screen shows today, and which parts of it are real versus placeholder.
- Whether the Home screen still has its full header (greeting, "Your week" title, avatar, streak pill), since it appears to be missing in the emulator.
- Which backend endpoints exist, and what `PUT /me` currently accepts.
- Whether an avatar or profile image column exists anywhere in the schema.

### 0c. Defects

List every layout, spacing or copy defect you can find. I have already spotted these, so confirm each one and add anything else:

1. **Internal roadmap language is leaking into user-facing strings.** The Progress tab reads "Progress arrives in phase 5" and the Profile screen reads "Weight editing arrives with the accounts feature in a later update". No user-facing string anywhere in the app may mention phases, roadmaps or future updates.
2. **A large empty gap sits above the title** on the Goals and Progress screens, before the first content.
3. **The Goals featured card shows the wrong time remaining.** On a Monday, a "this week" goal shows "Last day" in the featured card while the same week's goal in the list below shows "6 days left". The days-remaining calculation looks wrong, probably an off-by-one or a comparison against the wrong boundary. Find it, fix it, and add a unit test covering the first day, a middle day and the last day of both a week and a month period.
4. **The Home "This week" card shows BMI**, with a dash and "Add height in profile". BMI is a body measurement, not a weekly activity statistic, and it does not belong in that card. Replace that tile with the energy or calories figure the design specifies, and move BMI to the Profile screen.
5. **The Goals screen title reads "Your Goals"** where the design says "Goals".

### 0d. Plan

A numbered plan for Steps 1 to 8, adjusted for what already exists, with the files you expect to create or change. **If geolocation turns out to be missing or incomplete, say so at the top of your report and propose it as the first step, ahead of the cosmetic work.**

**Then stop and wait for my approval.**

---

## Step 1: Responsive layout and spacing pass

The app currently has sizing, padding and margin problems, and it has only been checked on one screen size. Make it work properly across devices.

- **Audit every layout** against the design system. Replace any hardcoded `dp` or `sp` value with a dimension resource. Every spacing value must come from the 8dp scale already defined in `dimens.xml`.
- **Window insets.** Handle status bar, navigation bar and gesture insets correctly using `WindowInsetsCompat`, so content never sits under the system bars and never leaves an unexplained gap below them. This is the likely cause of the empty space above the Goals and Progress titles.
- **Small screens.** Verify every screen on a 5 inch device at 320dp width. Nothing may clip, overlap or push a primary button off screen. Any screen whose content can exceed the viewport goes inside a `NestedScrollView`.
- **Large screens and tablets.** Add a `values-sw600dp` dimension set that increases the horizontal margin and caps the content width, so lists and cards do not stretch to full width on a tablet.
- **Landscape.** Every screen must be usable in landscape. Forms and the dashboard scroll rather than crop.
- **Font and display scaling.** Test at the largest system font size and the largest display size. Text must not clip or truncate mid-word. Use `sp` for all text and never a fixed height on a text container.
- **Touch targets.** Every interactive element is at least 48dp by 48dp.
- **Accessibility.** Every icon button, FAB and image has a content description. Run through the app once with TalkBack and fix anything unreadable.

Report a short before and after note per screen in the dev log. This is direct evidence for the assignment's "visually appealing user interface and seamless navigation" requirement.

---

## Step 2: Home screen fixes

- **Remove the Goals section from the Home screen entirely.** Goals has its own tab, so the duplicate belongs there only. Remove the section, its "See all" link, the adapter and view holder if they are used nowhere else, and any now-unused ViewModel state. Leave no dead code behind. Note in the dev log that this reverses the Home goals work from the previous phase, and why.
- **Replace the BMI tile** in the "This week" card with the energy figure (kilocalories) from the original design. BMI moves to Profile in Step 4.
- **Restore the full Home header** if it is missing: the greeting caption, the "Your week" title, the circular avatar on the right, and the streak pill. The avatar opens Profile.
- Re-check the week strip against the design: day letters with dates, today as a dark pill, and a lime dot under days that have a logged workout.

---

## Step 3: Profile backend

Build the backend before the UI, and test it with curl or `api-tests.http` before touching Android.

Extend the API, following every existing convention: PDO prepared statements, the standard JSON envelope, per-user scoping and server-side validation.

| Method | Path | Purpose |
|---|---|---|
| PUT | `/me` | update full name, weight and height |
| POST | `/me/avatar` | upload a profile picture (multipart) |
| DELETE | `/me/avatar` | remove the current profile picture |
| DELETE | `/me` | delete the account and all its data |

Rules:

- **Email is never updatable.** If the request body contains an email field, ignore it. Do not return an error, just never apply it. State this in the dev log as a deliberate decision, since changing an email is an identity change that needs a verification flow the project does not have.
- **Validation:** name between 2 and 60 characters, weight between 20 and 300 kg, height between 50 and 250 cm. Reject anything outside those ranges with a per-field error.
- **Avatar upload security.** This is the only place in the project that accepts a file, so treat it carefully and write the reasoning into the dev log, because it is strong Task 3b material:
  - Accept JPEG and PNG only, and verify the type by inspecting the image itself with `getimagesize()`, never by trusting the client's declared MIME type or the file extension.
  - Enforce a maximum size of 2 MB.
  - Re-encode the image server side rather than storing the uploaded bytes directly, and resize it to a maximum of 512 by 512 pixels. Re-encoding strips any payload hidden inside the file.
  - Generate the stored filename from `bin2hex(random_bytes(16))` plus the correct extension. Never reuse the client's filename.
  - Store files in `fitness-api/uploads/avatars/`, and add a `.htaccess` in that folder that disables PHP execution and allows only image types to be served.
  - Delete the previous avatar file when a new one replaces it, and when the account is deleted.
  - Store only the relative path in the `users` table, not the full URL, so the API works on any host.
- **Account deletion.** Require the user's current password in the request body and verify it with `password_verify` before deleting anything. Delete inside a transaction, removing the avatar file, the auth tokens, the goals, the strength sets, the workouts, any route points, and finally the user row. Confirm the foreign keys cascade correctly, and add the cascade explicitly where it is missing.
- Add a migration for the new `avatar_path` column. The database is already seeded, so **show me the migration before running it**.
- Add every new endpoint to `backend/api-tests.http`, including a rejected upload and a wrong-password delete attempt.

---

## Step 4: Profile screen rebuild

Rebuild the Profile screen from `docs/design/profile_screen/screen.png`, with these capabilities:

- **Profile picture.** Tap the avatar to choose a photo. Use the Android photo picker (`ActivityResultContracts.PickVisualMedia`), which needs no storage permission on modern Android and falls back gracefully on older versions. Show the upload progress on the avatar itself, and handle failure without losing the rest of the screen. Offer "Remove photo" when one is set. Propose an image loading library for display and wait for my approval before adding it.
- **Editable name.** Inline edit or a small dialog, with the same validation as the server.
- **Editable weight and height**, each with its unit, and the helper text explaining that weight is used to estimate calories burned. Changing the weight must update the calorie figures shown elsewhere in the app, since they are derived from it.
- **Email shown but not editable**, with a one-line explanation beneath it.
- **BMI**, calculated from the stored weight and height, displayed with its category label. Show a friendly prompt to add height when height is missing, instead of a blank value.
- **Three summary tiles**: total workouts, total distance, and member since.
- **Delete account.** Place it at the bottom, visually separated, in the error colour. Tapping it opens a confirmation dialog that states plainly that this permanently deletes the account and all workouts and cannot be undone, and requires the password to be typed before the confirm button enables. On success, clear the stored token and return to the authentication flow.
- **Log out** stays where it is.
- Every piece of copy is a string resource. No mention of phases or future updates anywhere.

---

## Step 5: Progress and analytics screen

This closes a real assignment gap. The brief requires "detailed statistics for each activity" and the ability to "monitor progress over time", and the Progress tab is currently empty.

Build it from `docs/design/progress_screen/screen.png`:

- **A period selector**: Week, Month, Year.
- **Activity filter chips**: All, Running, Cycling, Weightlifting.
- **A bar chart of activity per period**, for example active minutes per week within the selected month.
- **A trend chart** that changes with the selected activity: pace over time for running, average speed for cycling, and total volume for weightlifting.
- **Per-activity statistics**, which is the part the brief names explicitly. For the selected period show, for each activity type that has data:
  - Running: total distance, total time, average pace, best pace, longest run
  - Cycling: total distance, total time, average speed, max speed, longest ride
  - Weightlifting: total sessions, total volume, total sets, most frequent exercise
- **Personal records**: fastest 5 km, longest run, longest ride, and the best estimated one-rep max per exercise.
- Loading, empty and error states. The empty state tells the user to log a workout to see statistics.

Implementation notes:

- Put every calculation in a testable class with no Android dependencies, in the same style as the existing domain model, and **unit test it**. Reuse the existing workout model methods rather than recalculating pace, speed or volume in a new place.
- Propose a charting library and wait for my approval. MPAndroidChart is the common choice for XML Views. If you would rather draw the bar chart with a custom `View`, propose that instead with the trade-off, since a hand-drawn chart is one fewer dependency for the marker to resolve and is a stronger thing to write about in the report.
- Charts must respect the design system: lime for the highlighted series, neutral tones elsewhere, minimal gridlines, and readable in both light and dark themes.

---

## Step 6: Geolocation completion (conditional on the Step 0 audit)

**Only run this step if the Step 0 audit found geolocation missing or incomplete.** The assignment names geolocation as a required component of Task 4, which carries 40 of the 100 marks, so nothing else in this phase matters more.

A complete implementation needs:

- Runtime permission handling for `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, with the priming screen already built leading into the system dialog, and a working path for the user who denies it, since manual logging must still work.
- `FusedLocationProviderClient` with a sensible update interval, and points with poor accuracy discarded. Keep the accuracy threshold in one named constant and mention it in the dev log, because sensor accuracy is a discussion point in the written report.
- A live tracking screen showing elapsed time, distance and pace updating as the workout runs, with start, pause and stop.
- A foreground service so tracking continues with the screen off, declaring the `location` service type and its matching permission.
- Route points saved with the workout and redisplayed on the workout detail screen.
- Verification using the emulator's extended controls to play back a GPX route, so this can be demonstrated and screenshotted without going outside.

If geolocation is already complete, use this step instead to verify it end to end and capture the screenshots for the report.

---

## Step 7: Personalised insights

The brief lists advanced analytics and personalised insights as what separates a good app from a basic one, and the Home screen already has a placeholder card reading "Keep logging workouts to unlock personalised insights".

Make that card real. Generate insights with simple rules over the user's own data, with no machine learning involved:

- "You ran 15% further than last week."
- "You have not cycled in 9 days."
- "New personal record on bench press."
- "At this rate you will reach your monthly goal by the 24th."
- "Your average pace improved by 12 seconds per kilometre this month."

Rules:

- Put the rules in one testable class that takes the workout list and returns a ranked list of insights, and unit test each rule.
- Show the single most relevant insight on Home, choosing by a priority order you define and document.
- Keep the existing placeholder copy as the fallback when there is not enough data, and define explicitly what "enough data" means.

---

## Step 8: Submission readiness

The assignment states that media which cannot be run directly may be awarded a fail, and the marker will most likely run the backend on XAMPP on Windows rather than Docker. Make the submission bulletproof.

- **Verify the XAMPP path end to end.** Export the schema and seed, import them into a MariaDB instance, and confirm nothing fails. Collations must be `utf8mb4_unicode_ci`, with no MySQL 8 only syntax anywhere.
- **Refresh `backend/README.md`** with both routes, Docker and XAMPP, numbered step by step, including the test login, the emulator base URL and how to change it.
- **Add a root `README.md`** covering what the app is, how to open it in Android Studio, how to start the backend, the test credentials, and a known limitations section.
- **Check the seed data** produces a populated, good-looking app on first run: workouts across all three activity types spread over recent weeks, at least two goals in progress, and a profile with weight and height set so BMI and calories display properly.
- **Confirm a clean clone builds.** No absolute paths, no missing files in git, and `local.properties` correctly ignored.
- **Capture report screenshots** into `docs/screenshots/`, each one clearly showing a required element: the RecyclerView, the FloatingActionButton, geolocation in use, the live tracking screen, the MySQL tables in phpMyAdmin, and the three inheritance hierarchies in the code. Name them so I can reference them as figures.
- **Produce `docs/assignment-coverage.md`**, a table mapping every requirement in the brief to where it is implemented, with the file path or screenshot for each. This is my checklist before I submit, and it tells me immediately if anything is still missing.

---

## Definition of done

- [ ] `assembleDebug` and `testDebugUnitTest` pass
- [ ] Every screen is usable on a 5 inch phone, a tablet, in landscape, and at the largest system font size
- [ ] No user-facing string mentions phases, roadmaps or future updates
- [ ] The Goals section is gone from Home, with no dead code left behind
- [ ] Days remaining is correct on both the featured goal card and the goal list, with unit tests covering the boundaries
- [ ] Profile supports a profile picture, editable name, weight and height, a read-only email, BMI, and account deletion with password confirmation
- [ ] Avatar uploads are validated, re-encoded, randomly named, and served from a folder where PHP cannot execute
- [ ] Deleting an account removes every related row and the avatar file
- [ ] The Progress tab shows per-activity statistics, trends over time and personal records
- [ ] Geolocation is verifiably working, with screenshots captured
- [ ] The insight card on Home shows a real insight derived from the user's data
- [ ] `docs/assignment-coverage.md` maps every brief requirement to its implementation
- [ ] The backend imports and runs on MariaDB and XAMPP
- [ ] `docs/dev-log.md` has an entry for every step, and each step is a separate commit

## Out of scope
Email changing, password reset or change, social or sharing features, new activity types beyond the three required, wearable integration, and any redesign beyond matching the existing Stitch designs.

## Final report when this phase is complete
1. A short summary of what changed.
2. The assignment coverage table, so I can see at a glance what is complete.
3. The new endpoints with example requests and responses.
4. The security measures on the avatar upload and the account deletion, and what a production app would still need.
5. Anything left as a TODO, and anything you think risks a mark being lost.
