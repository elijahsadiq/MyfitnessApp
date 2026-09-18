# Fitness API — backend

Two-service PHP/MySQL backend for the My Fitness App Android client. Runs
locally in Docker for development, or as a bare-metal drop-in for the
XAMPP + phpMyAdmin combination the marker is expected to use.

```
backend/
├── docker-compose.yml         Dev stack (mysql + php-apache + phpmyadmin)
├── docker/php/                Custom PHP-Apache image (pdo_mysql, mod_rewrite)
├── sql/
│   ├── 01_schema.sql          Tables + indexes + foreign keys
│   └── 02_seed.sql            Test user + 9 sample workouts
├── fitness-api/               The actual PHP API — populated in Step 3
└── api-tests.http             End-to-end request suite (added in Step 3)
```

The PHP is deliberately **framework-free and Composer-free**: the marker can
copy `fitness-api/` into XAMPP's `htdocs`, import the two SQL files via
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

| Service      | URL                        | Notes                                          |
|--------------|----------------------------|------------------------------------------------|
| PHP / API    | http://localhost:8080/     | Apache 2.4 + PHP 8.3; serves `fitness-api/`    |
| phpMyAdmin   | http://localhost:8081/     | login as `root` / `rootpassword`               |
| MySQL        | localhost:3306             | root/`rootpassword`, or `fitness_user`/`fitness_pass` |

Reset the database (drop the named volume and re-seed):

```bash
docker compose down -v && docker compose up -d
```

The Android emulator reaches the host via `10.0.2.2`, so the emulator base
URL is:

```
http://10.0.2.2:8080/fitness-api/
```

## Option B — XAMPP (for the marker)

1. Install XAMPP (any 8.x version). Start **Apache** and **MySQL**.
2. Copy the entire `backend/fitness-api/` folder into XAMPP's `htdocs`
   directory (e.g. `/Applications/XAMPP/htdocs/fitness-api/` on macOS or
   `C:\xampp\htdocs\fitness-api\` on Windows).
3. Open http://localhost/phpmyadmin/ and import the two SQL files **in
   order**: `sql/01_schema.sql` first, then `sql/02_seed.sql`.
4. The API is now at `http://localhost/fitness-api/`. The Android
   emulator reaches it at:

   ```
   http://10.0.2.2/fitness-api/
   ```

   No port suffix, because XAMPP's Apache listens on port 80.

The schema declares `utf8mb4` / `utf8mb4_unicode_ci` explicitly on every
table so the import succeeds on both MariaDB (XAMPP) and MySQL 8 (Docker).

---

## Test credentials

Seeded by `sql/02_seed.sql`:

- **Email:** `alex@example.com`
- **Password:** `Password123`

The stored `password_hash` is a genuine bcrypt cost-10 digest (`$2b$…`);
PHP's `password_verify()` accepts it directly. Use the app's login flow or
the request suite in `api-tests.http` (added in Step 3) to authenticate.

---

## Development tips

- **Tail Apache access + error logs**: `docker compose logs -f php`
- **MySQL shell**: `docker compose exec mysql mysql -uroot -prootpassword fitness_app`
- **Nuke everything (containers + volumes + images)**: `docker compose down -v --rmi local`
- **Just restart the API without touching the DB**: `docker compose restart php`

The PHP container mounts `./fitness-api/` as a live volume, so edits to
`.php` files are picked up on the next request — no restart needed.
