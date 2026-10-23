# Fitness API — backend

Two-service PHP/MySQL backend for the My Fitness App Android client. Runs
locally in Docker for development, in the cloud on Render + Aiven MySQL
(the `cloud` app build talks to it), or as a bare-metal drop-in for the
XAMPP + phpMyAdmin combination the marker is expected to use.

```
backend/
├── Dockerfile              PHP 8.3 + Apache image (pdo_mysql, GD, mod_rewrite),
│                           used by docker-compose AND by Render
├── docker-compose.yml      Dev stack (mysql + php-apache + phpmyadmin)
├── docker/php/             Apache override (.htaccess allowed in the docroot)
├── certs/aiven-ca.pem      Public CA certificate of the Aiven MySQL server
├── sql/
│   ├── 01_schema.sql       Authoritative schema for a fresh install (users,
│   │                       auth_tokens, workouts, strength_sets, route_points, goals)
│   ├── 02_seed.sql         Test user + 9 workouts across types + 5 goals
│   │                       (main active, two more active, completed, ended)
│   └── migrations/         Historical migrations, NOT auto-run on a fresh install.
│                           Only applied against an existing DB that pre-dates
│                           the shape in 01_schema.sql.
│       ├── 03_migration_goals_phase7.sql
│       ├── 04_migration_route_points_phase8a.sql
│       ├── 05_migration_profile_phase8.sql
│       ├── 06_migration_goals_phase8b.sql
│       ├── 07_migration_avatar_blob_phase9.sql
│       └── 08_migration_swim_row_phase8d.sql
├── fitness-api/
│   ├── index.php           Front controller
│   ├── core/               Router, Response, Auth, Validator, Database
│   ├── controllers/        Auth / User / Workout / Goal
│   └── models/             User / Workout / Goal (PDO data mappers)
└── api-tests.http          End-to-end request cases
```

The PHP is deliberately **framework-free and Composer-free**: the marker
can copy `fitness-api/` into XAMPP's `htdocs`, import the SQL files via
phpMyAdmin, and the app works. Docker is just for developer convenience.

---

## Option A — Docker (recommended for development)

Requires Docker Desktop (or OrbStack) 4.x+ on macOS / Linux / Windows.
Everything is arm64-native so Apple Silicon runs without QEMU emulation.

```bash
cd backend
docker compose up -d
```

Ports:

| Service      | URL                        | Notes                                                 |
|--------------|----------------------------|-------------------------------------------------------|
| PHP / API    | http://localhost:8080/     | Apache 2.4 + PHP 8.3 (GD + pdo_mysql + mod_rewrite)   |
| phpMyAdmin   | http://localhost:8081/     | login as `root` / `rootpassword`                      |
| MySQL        | localhost:3307             | root/`rootpassword`, or `fitness_user`/`fitness_pass` |

Reset the database (drops the named volume and re-seeds from scratch):

```bash
docker compose down -v && docker compose up -d
```

Migrations in `sql/migrations/` are only ever needed if you're walking
an older DB forward to the current shape. **A fresh install never runs
them** — `01_schema.sql` already produces the final shape in one pass.
If you do need to walk an old DB forward:

```bash
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/03_migration_goals_phase7.sql
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/04_migration_route_points_phase8a.sql
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/05_migration_profile_phase8.sql
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/06_migration_goals_phase8b.sql
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/07_migration_avatar_blob_phase9.sql
docker exec -i fitness-mysql mysql -uroot -prootpassword fitness_app \
    < sql/migrations/08_migration_swim_row_phase8d.sql
```

Migrations 07 and 08 are idempotent (safe to run twice).

After pulling a change to `Dockerfile`, rebuild the API container with
`docker compose up -d --build php` (the database is untouched).

The Android emulator reaches the host via `10.0.2.2`, so the emulator
base URL is:

```
http://10.0.2.2:8080/
```

(The emulator app's `BASE_URL` is already set to this for the `local`
flavor — see `app/build.gradle.kts`.)

## Option B — XAMPP (for the marker)

Tested against XAMPP 8.2 on Windows 10 with MariaDB 10.4.

1. Install XAMPP (any 8.x version). Start **Apache** and **MySQL**.
2. Copy the entire `backend/fitness-api/` folder into XAMPP's `htdocs`
   directory (e.g. `/Applications/XAMPP/htdocs/fitness-api/` on macOS or
   `C:\xampp\htdocs\fitness-api\` on Windows).
3. Open <http://localhost/phpmyadmin/> and import **two files only, in
   this order**, via the Import tab:
   - `sql/01_schema.sql` *(authoritative schema — includes the goals,
     avatar and route-points columns in one pass; no migrations needed)*
   - `sql/02_seed.sql` *(test user + workouts + goals)*

   Files under `sql/migrations/` are historical; do not import them.
4. The API is now at `http://localhost/fitness-api/`. The Android
   emulator reaches it at:

   ```
   http://10.0.2.2/fitness-api/
   ```

   No port suffix — XAMPP's Apache listens on port 80. Change the
   emulator base URL by editing the `BASE_URL` in `app/build.gradle.kts`
   (the `local` flavor's `buildConfigField`) and rebuilding.

The schema declares `utf8mb4` / `utf8mb4_unicode_ci` explicitly on every
table so the import succeeds on both MariaDB (XAMPP) and MySQL 8
(Docker). None of the migrations use MySQL-8-only syntax — the
`add_col_if_absent` prepared statement wrapper is portable.

### phpMyAdmin — verifying the data arrived

After the imports, click **Browse** on each table in the sidebar:

- `users` → one row, `alex@example.com`.
- `workouts` → nine rows across all three activity types.
- `strength_sets` → fourteen rows across the three strength workouts.
- `goals` → three in-progress goals.
- `route_points` → empty until the user tracks a real GPS workout.
- `auth_tokens` → populated once anyone logs in.

## Option C — Cloud: Render + Aiven MySQL

This is what the `cloud` build of the app (and the APK handed in) uses:
`https://elijah-fitness-api.onrender.com/`.

| Part | Where | Notes |
|---|---|---|
| PHP API | Render free web service (Docker, Singapore) | Defined in `render.yaml` at the repo root. Auto-deploys on every push that touches `backend/`. |
| MySQL 8.4 | Aiven free plan (DigitalOcean, Singapore) | Render has no free MySQL. Encrypted connection; the API verifies the server against `certs/aiven-ca.pem`. |

The connection settings (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASS`) live
only in Render's environment settings — never in Git. `DB_SSL_CA` turns
on certificate-verified TLS (unset for Docker / XAMPP).

**Free-tier behaviour to know about**
- The API sleeps after 15 minutes without traffic; the next request
  wakes it in about a minute. The app waits up to 90 s and pings
  `/health` at launch so the server is usually awake by the time you
  log in. Open `/health` in a browser to wake it manually.
- Render's disk is wiped on every restart, which is why avatars are
  stored in MySQL (`users.avatar_jpeg`), not as files.
- Aiven may power off a free database after a long idle spell (it emails
  first); power it back on from the Aiven console.

**Loading the cloud database** (uses the MySQL client inside the local
Docker container):

```bash
docker cp certs/aiven-ca.pem fitness-mysql:/tmp/aiven-ca.pem
docker exec -i -e MYSQL_PWD='<password>' fitness-mysql mysql \
    -h <aiven-host> -P <port> -u avnadmin \
    --ssl-mode=VERIFY_IDENTITY --ssl-ca=/tmp/aiven-ca.pem \
    < sql/01_schema.sql      # then the same with sql/02_seed.sql
```

The seed's dates are relative to "now", so re-seeding just before a demo
makes the demo account look current. The seed doesn't clear old rows:
to reset, first run the same command with `-e "DROP DATABASE fitness_app"`
instead of a file — this deletes **every** account, including any a
tester created.

Aiven runs MySQL in ANSI mode by default (`"x"` is a column name, `||`
joins strings), so the API pins MySQL 8's default `sql_mode` on every
connection (`core/Database.php`). Load only `01_schema.sql` and
`02_seed.sql` (plus migrations 07 and 08 into a database created before
them; both use single-quoted strings only): migrations 03–06 use
double-quoted strings and fail in ANSI mode — a fresh install never
needs them.

Apply a new migration to Aiven **before** pushing the PHP that uses it:
Render redeploys on every push that touches `backend/`, and the new code
writes the new columns.

---

## Test credentials

Seeded by `sql/02_seed.sql`:

- **Email:** `alex@example.com`
- **Password:** `Password123`

The stored `password_hash` is a genuine bcrypt cost-10 digest (`$2b$…`);
PHP's `password_verify()` accepts it directly. Use the app's login flow
or the request suite in `api-tests.http` to authenticate.

Seeded profile has weight 70.0 kg and height 178.0 cm set, so BMI
displays correctly on Profile and the Home card's kcal tile has a body
weight to multiply against.

---

## API surface

```
Auth
  POST   /auth/register
  POST   /auth/login
  POST   /auth/logout

User (phase 2 + phase 8)
  GET    /me
  PUT    /me
  POST   /me/avatar          (multipart; JPEG/PNG; re-encoded to 512×512)
  DELETE /me/avatar
  DELETE /me                 (body { "confirm": "DELETE" })
  GET    /avatars/{name}.jpg (public — the stored photo; name is random)

Workouts (phase 2 + phase 8a + phase 8c + phase 8d)
  GET    /workouts           (filters: ?type, ?from, ?to)
  POST   /workouts           (body may carry optional routePoints[])
  GET    /workouts/{id}      (returns the stored route if any)
  PATCH  /workouts/{id}      (body { "notes": "..." }; only notes are editable)
  DELETE /workouts/{id}

Goals (phase 7 + phase 8b)
  GET    /goals
  POST   /goals
  PUT    /goals/{id}
  DELETE /goals/{id}
  POST   /goals/{id}/notified
  POST   /goals/{id}/main
  POST   /goals/{id}/extend  (body { "days": 7 })
```

Every endpoint except `/auth/*`, `/health` and `/avatars/*` requires an
`Authorization: Bearer <token>` header. Tokens come back from
`/auth/login` only (`/auth/register` creates the account and returns
`{ user }`; the user then logs in) and are stored only as a SHA-256
hash on the server.

### Workout fields

JSON keys are camelCase. Every workout response (list, detail, create,
PATCH) has the same shape; the list leaves `routePoints` empty.

| Field | Type | Notes |
|---|---|---|
| `clientUuid` | string | Device-generated; re-POSTing the same UUID returns the first row (200) |
| `activityType` | string | `RUNNING`, `CYCLING`, `WEIGHTLIFTING`, `SWIMMING`, `ROWING` |
| `startTime` | ISO-8601 UTC | |
| `durationSeconds` | int | 1 to 86 400 |
| `distanceMeters` | number or null | Required (> 0) for every type except `WEIGHTLIFTING` |
| `poolLengthM` | int or null | Phase 8d. `SWIMMING` only, 10 to 100 |
| `laps` | int or null | Phase 8d. `SWIMMING` only, 1 to 400 |
| `caloriesKcal` | int or null | Phase 8d. Any type, 0 to 20 000. The app works it out at save time from the profile weight; older rows are `null` |
| `notes` | string or null | Up to 2 000 characters |
| `sets` | array | Required for `WEIGHTLIFTING` only |
| `routePoints` | array | `RUNNING`, `CYCLING` and `ROWING` only (422 for the others) |

`laps` and `poolLengthM` are optional on a swim but must be sent
together; the app sends `distanceMeters = laps × poolLengthM`. Sending
either one on another activity type is a 422. A JSON `null` counts as
"not sent".

### Goal activity rules

`activityType` is one of the five workout types, or `null` for "any
activity".

| `goalType` | Allowed `activityType` |
|---|---|
| `distance` (km) | `RUNNING`, `CYCLING`, `SWIMMING`, `ROWING` |
| `duration` (minutes) | any type, or `null` |
| `count` (sessions) | any type, or `null` |
| `volume` (kg lifted) | `WEIGHTLIFTING` only |

---

## Development tips

- **Tail Apache access + error logs**: `docker compose logs -f php`
- **MySQL shell**: `docker compose exec mysql mysql -uroot -prootpassword fitness_app`
- **Nuke everything (containers + volumes + images)**: `docker compose down -v --rmi local`
- **Just restart the API without touching the DB**: `docker compose restart php`

The PHP container mounts `./fitness-api/` as a live volume, so edits to
`.php` files are picked up on the next request — no restart needed.

---

## Avatar upload — security notes

`POST /me/avatar` is the only endpoint that accepts a file. Six layers
of defence apply before anything is stored:

1. `is_uploaded_file()` + the raw `UPLOAD_ERR_OK` code — rejects swapped tmp paths.
2. 2 MB cap enforced **before** reading pixels.
3. `getimagesize()` parses the file's magic bytes. The client's declared
   `$_FILES['file']['type']` and the filename extension are never trusted.
4. Only `IMAGETYPE_JPEG` and `IMAGETYPE_PNG` are allowed through.
5. GD re-encodes the image into a fresh 512×512 JPEG at quality 85.
   Re-encoding strips any payload hidden inside the original bytes.
6. The re-encoded JPEG is stored in MySQL (`users.avatar_jpeg`), never
   in the web root, so there is no uploaded file Apache could be tricked
   into executing. It is served by `GET /avatars/{name}.jpg`, where the
   name is `bin2hex(random_bytes(16))` (new on every upload; the client's
   filename is never kept) and is validated against `^[a-f0-9]{32}\.jpg$`
   before any query runs.

See `fitness-api/controllers/UserController.php` (`uploadAvatar` and
`avatarImage`) for the code.
