# FinTrack — Development Guide

Everything a developer needs: stack, build & release pipeline, signing,
versioning scheme, and where the hard rules live.

- **[DEV.md](../DEV.md)** — full architecture and code guide (layers, schema,
  migrations, security, UI conventions). Read it before touching code.
- **[AGENTS.md](../AGENTS.md)** — mandatory development rules (build policy,
  DB/UI/UX requirements). These are non-negotiable.

## Tech stack

| Component | Version / Choice |
|---|---|
| Kotlin | 2.1.0 (JVM target 21) |
| AGP / KSP | 8.7.3 / 2.1.0-1.0.29 |
| UI | Jetpack Compose BOM 2024.12.01, Material 3 |
| DB | Room 2.6.1 (KSP) + SQLCipher 4.6.1 (`net.zetetic:sqlcipher-android`) |
| DI | Hilt 2.53.1 (+ navigation-compose) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| ABIs | arm64-v8a, armeabi-v7a |

No network libraries exist in the dependency graph — the app is offline by
design (`INTERNET` permission is absent from the manifest).

## Build & release pipeline

**Local builds are not used.** The APK is built and checked exclusively on
**GitHub Actions** after each push ([`.github/workflows/build.yml`](../.github/workflows/build.yml)).
Before pushing, review code manually: DAO ↔ Repository ↔ ViewModel ↔ Screen
signatures, imports, brackets, layer wiring (see the checklist in DEV.md §11).

Download the artifact from the **Actions** tab of the repository.

### Signing (repository secrets)

| Secrets | Result |
|---|---|
| `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` + `KEY_ALIAS` + `KEY_PASSWORD` | `assembleRelease` with the release key |
| no release, but `DEBUG_KEYSTORE_BASE64` + `DEBUG_KEYSTORE_PASSWORD` + `DEBUG_KEY_ALIAS` + `DEBUG_KEY_PASSWORD` | `assembleDebug` with a **fixed** debug key (updatable installs) |
| neither | `assembleDebug` with the runner's auto keystore (**incompatible between runs**) |

`app/build.gradle.kts` defines two signing configs: `release` (env
`KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`) and
`debugFixed` (env `DEBUG_KEYSTORE_*`). The debug build uses `debugFixed`
when those env vars are set, otherwise the auto keystore.

## Versioning

Three independent version spaces:

1. **App version** — [`app/build.gradle.kts`](../app/build.gradle.kts):
   `versionCode` (monotonic integer; must increase for every release, Android
   refuses to install over an equal/lower one) and `versionName`
   (`MAJOR.MINOR.PATCH-feature`, human label, shown in Settings → About via
   `BuildConfig.VERSION_NAME`).
2. **DB schema version** — `AppDatabase.VERSION` (currently 5). Any entity
   change requires: bump `VERSION` → write `MIGRATION_(N-1)_N` → register it
   in `DbHolder.build()` → bump `versionCode`. Without an explicit migration
   `fallbackToDestructiveMigration()` **deletes data**.
3. **Backup format** — `FINTX1` magic + JSON `format` field + `dbVersion`
   stamped at export; a backup from a newer schema is rejected on import.

## Adding features the project-approved way

- **New currency**: one enum entry in `Currency` — no migration needed.
- **New UI string**: field in `Strings` + values in **both** `StringsEn` and
  `StringsRu` (`AppLocale.kt`); never hardcode in composables.
- **New screen**: composable in `ui/screens/` + optional ViewModel +
  `composable("route")` in `AppNavGraph`.
- **Multi-table writes**: always `db().withTransaction { }`.
- **Lists**: keyset pagination (20/page), lazy load near the bottom; never
  full-table scans for stats or lists.

## Documentation currency

DEV.md must reflect the actual code state — update the matching section with
every significant change (schema, architecture, screens, security rules).
Outdated documentation is worse than none.
