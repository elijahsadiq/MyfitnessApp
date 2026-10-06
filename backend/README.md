# Fitness API — backend

Two-service PHP/MySQL backend for the My Fitness App Android client. Runs
locally in Docker for development, or as a bare-metal drop-in for the
XAMPP + phpMyAdmin combination the marker is expected to use.

```
backend/
├── docker-compose.yml      Dev stack (mysql + php-apache + phpmyadmin)
├── docker/php/             Custom PHP-Apache image (pdo_mysql, mod_rewrite, GD)
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
│       └── 06_migration_goals_phase8b.sql
├── fitness-api/
│   ├── index.php           Front controller
│   ├── core/               Router, Response, Auth, Validator, Database
│   ├── controllers/        Auth / User / Workout / Goal
│   ├── models/             User / Workout / Goal (PDO data mappers)
│   └── uploads/avatars/    Avatar storage + hardened .htaccess
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
```

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
  DELETE /me                 (body { password })

Workouts (phase 2 + phase 8a)
  GET    /workouts           (filters: ?type, ?from, ?to)
  POST   /workouts           (body may carry optional routePoints[])
  GET    /workouts/{id}      (returns the stored route if any)
  DELETE /workouts/{id}

Goals (phase 7)
  GET    /goals
  POST   /goals
  PUT    /goals/{id}
  DELETE /goals/{id}
  POST   /goals/{id}/notified
```

Every endpoint except `/auth/*` and `/health` requires a
`Authorization: Bearer <token>` header. Tokens come back from
`/auth/register` and `/auth/login` and are stored only as a SHA-256
hash on the server.

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
of defence apply before anything hits disk:

1. `is_uploaded_file()` + the raw `UPLOAD_ERR_OK` code — rejects swapped tmp paths.
2. 2 MB cap enforced **before** reading pixels.
3. `getimagesize()` parses the file's magic bytes. The client's declared
   `$_FILES['file']['type']` and the filename extension are never trusted.
4. Only `IMAGETYPE_JPEG` and `IMAGETYPE_PNG` are allowed through.
5. GD re-encodes the image into a fresh 512×512 JPEG at quality 85.
   Re-encoding strips any payload hidden inside the original bytes.
6. The stored filename is `bin2hex(random_bytes(16)) . '.jpg'`. The
   client's filename is never kept. Files land in
   `fitness-api/uploads/avatars/` which carries a `.htaccess` that
   disables PHP execution and denies serving any extension other than
   `.jpg`.

See `fitness-api/controllers/UserController.php::uploadAvatar` for the
code and `fitness-api/uploads/avatars/.htaccess` for the Apache hardening.
