# Finance Tracker (FinTrack)

Secure offline Android personal finance app with an encrypted local database.

## Features
- AES-256 encrypted database (Room + SQLCipher)
- Encryption key derived from the pattern lock via PBKDF2 (SHA-256, 120k iterations) —
  the pattern IS the source of the DB key; without it the database file cannot be decrypted
- Pattern stored as a SHA-256 hash with a random salt in SharedPreferences
- Auto-wipe of pattern and database after 3 failed attempts
- Multi-currency: RUB (default), USD, CNY, THB, PHP
- Active-currency history with keyset pagination (20 records per page) and lazy loading
- Incremental statistics in a dedicated `stats` table: totals and per-period sums
  (day / week / month / year) updated by delta on every add/remove — no full scans
- Compact per-period stats block under the balance card
- Record detail dialog with scrollable note, delete confirmation dialog
- Material 3 Jetpack Compose UI, dark/light theme
- RU / EN localization (all strings in both languages)
- Offline-first: no INTERNET permission, no accounts, no telemetry, backup disabled

## Build
Local builds are not used: the APK is built and checked on **GitHub Actions**
after each push (see [AGENTS.md](AGENTS.md) for the full development rules).

## GitHub Actions Secrets (optional, for signed release)
`KEYSTORE_BASE64` (base64-encoded keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` — see [`.github/workflows/build.yml`](.github/workflows/build.yml);
without them CI builds a debug APK.

## Documentation
- [AGENTS.md](AGENTS.md) — mandatory development rules (build, DB, UI conventions)
- [DEV.md](DEV.md) — full architecture and code guide for continuing development

## License
Apache 2.0