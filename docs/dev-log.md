# Dev log — Phase 1

Short notes for each step: what was built, the key design decision, one rejected alternative and its reason, and which assignment task the step supports.

---

## Step 0 — Inspection (read-only)
- Confirmed the Android Studio template is Empty Views (XML), not Compose.
- Read `phase-1-foundation.md`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, manifest.
- Baseline: AGP 9.4.0, Gradle 9.6.0, `minSdk 27`, `targetSdk 37`, `compileSdk 37`, Java 11.
- Flagged: `namespace` was `com.example.myfitnessapp`; the manifest declared no activity; no Kotlin plugin was applied even though `.kt` test files exist.
- Supports: **Task 3b (design)** — used to lock the project foundation before diagrams are drawn.

## Step 1 — Build configuration + package rename
**Built**
- Renamed the package from `com.example.myfitnessapp` to `com.elijah.fitness` (namespace, applicationId, source tree under `app/src/{main,test,androidTest}/java/com/elijah/fitness`, and the `useAppContext` assertion in `ExampleInstrumentedTest`).
- Enabled `buildFeatures { viewBinding = true }` in `app/build.gradle.kts`.
- Added phase-1 dependencies through `gradle/libs.versions.toml`: `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material:material` (Material 3), `androidx.constraintlayout`, `androidx.recyclerview`, `androidx.fragment:fragment-ktx`, `androidx.navigation:navigation-fragment-ktx` and `navigation-ui-ktx`, `androidx.lifecycle:lifecycle-viewmodel-ktx` and `lifecycle-runtime-ktx`, `kotlinx-coroutines-android`, and (test-only) `kotlinx-coroutines-test`.
- Verified with `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` — both pass.

**Key design decision**
- **Do not apply the `org.jetbrains.kotlin.android` plugin.** AGP 9.4.0 registers a `kotlin` extension itself, so adding the standalone Kotlin plugin fails with `Cannot add extension with name 'kotlin'`. Kotlin sources still compile because AGP 9 provides Kotlin support natively (`compileDebugKotlin` and `compileDebugUnitTestKotlin` run without the plugin). A comment in `libs.versions.toml` records this.

**Rejected alternative**
- Downgrading to AGP 8.x to use the familiar standalone Kotlin plugin. Rejected because the user asked to keep AGP on 9.x, and downgrading would fight the template Android Studio generated.

**Kotlin/Android concepts (for my notes)**
- *ViewBinding*: a generated typed class per XML layout — replaces `findViewById` and avoids the null-safety and typo risks of `synthetic` imports.
- *Version catalog* (`libs.versions.toml`): a single TOML file that declares versions, library coordinates, and plugin ids. Modules reference them as `libs.androidx.core.ktx` etc., which makes bumps a one-file change.

**Assignment task supported**
- **Task 4 (development)** — the app can now compile and depend on the AndroidX/Material libraries the rest of phase 1 needs. No production code yet.
