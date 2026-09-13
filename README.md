# Finance Tracker
Secure Android personal finance app with encrypted local storage.
## Features
- AES-256 encrypted database (Room + SQLCipher)
- Key wrapped by Android Keystore, stored in EncryptedSharedPreferences
- Pattern lock authentication (NOT used as encryption key)
- Auto-wipe DB after 3 failed pattern attempts
- Multi-currency: RUB (default), USD, CNY, THB, PHP
- Material 3 Jetpack Compose UI
- Offline-first, no internet required
## Build
./gradlew assembleRelease
## GitHub Actions Secrets (optional)
KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
## License
Apache 2.0