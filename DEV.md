# DEV.md — полное руководство по устройству FinTrack

Этот документ описывает архитектуру, код и все соглашения проекта так, чтобы
разработчик, не видевший проект раньше, мог продолжить и развивать его с нуля.
Перед правками обязательно прочитайте [`AGENTS.md`](AGENTS.md) — там жёсткие
правила сборки и качества.

---

## 1. Что это за проект

FinTrack — офлайн-приложение для учёта личных финансов на Android.
Особенности:

- **Полная офлайн-работа**: нет сети, нет аккаунтов, нет облака, нет телеметрии.
  В [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) нет разрешения `INTERNET`.
- **Шифрование БД**: Room + SQLCipher (AES-256). Ключ шифрования выводится из
  графического узора через PBKDF2. Без правильного узора файл БД физически
  не расшифровать.
- **Авто-вайп**: после 3 неверных узоров удаляются узор, соль и файл БД.
- **Мультивалютность**: RUB (по умолчанию), USD, CNY, THB, PHP.
- **UI**: Jetpack Compose, Material 3, тёмная/светлая тема, RU/EN локализация.
- **Производительность**: keyset-пагинация по 20 записей, инкрементальная
  статистика в отдельной таблице (без полного скана транзакций).

Ключ шифрования выводится из графического узора (см. §5):
`PBKDF2(SHA-256(узор), соль)` — см. [`PatternLockManager.derivePassphrase()`](app/src/main/java/com/example/financetracker/data/security/PatternLockManager.kt:56).

---

## 2. Инструменты и версии

См. [`gradle/libs.versions.toml`](gradle/libs.versions.toml) и
[`app/build.gradle.kts`](app/build.gradle.kts):

- Kotlin 2.1.0, AGP 8.7.3, KSP 2.1.0-1.0.29
- Compose BOM 2024.12.01, Material3
- Room 2.6.1 (runtime + ktx + compiler через KSP)
- SQLCipher 4.6.1 (`net.zetetic:sqlcipher-android`)
- Hilt 2.53.1 (Dagger + navigation-compose)
- minSdk 26, targetSdk/compileSdk 35, JVM target 21
- `versionCode`/`versionName` — в [`app/build.gradle.kts`](app/build.gradle.kts:15):
  `versionCode` монотонно растёт при каждом релизе (без его подъёма APK не
  установится поверх), `versionName` — человекочитаемая метка. Включён
  `buildFeatures { buildConfig = true }` — `BuildConfig.VERSION_NAME`
  показывается в разделе «О приложении» настроек (см. §14).
- Валюты: RUB, USD, CNY, THB, PHP, EUR, TRY, EGP, AED, VND, BYN
  ([`Currency`](app/src/main/java/com/example/financetracker/data/model/Currency.kt));
  добавление валюты миграции БД не требует (см. §10).

### Как собирать и проверять (ВАЖНО)
**Локальный билд запрещён** (см. [`AGENTS.md`](AGENTS.md)). Сборка и проверка
ошибок — только на GitHub Actions после push. Перед push проверяйте код
вручную: сигнатуры, импорты, скобки, связи слоёв.

**Подпись APK** (см. [`.github/workflows/build.yml`](.github/workflows/build.yml)):

| Секреты GitHub Actions | Результат |
|---|---|
| `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` + `KEY_ALIAS` + `KEY_PASSWORD` | `assembleRelease` с release-ключом. |
| Нет release, но `DEBUG_KEYSTORE_BASE64` + `DEBUG_KEYSTORE_PASSWORD` + `DEBUG_KEY_ALIAS` + `DEBUG_KEY_PASSWORD` | `assembleDebug` с **фиксированным** debug-ключом (обновление без удаления). |
| Нет обоих | `assembleDebug` с авто-кейстором раннера (**несовместимо между запусками**). |

`app/build.gradle.kts` определяет два `signingConfig`: `release` (env
`KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`) и
`debugFixed` (env `DEBUG_KEYSTORE_PATH`/`DEBUG_KEYSTORE_PASSWORD`/
`DEBUG_KEY_ALIAS`/`DEBUG_KEY_PASSWORD`). Debug-сборка использует
`debugFixed`, если соответствующие env заданы, иначе — авто-кейстор.

---

## 3. Карта исходников

Корень пакетов: `app/src/main/java/com/example/financetracker/`

```
FinanceTrackerApp.kt      — @HiltAndroidApp; грузит нативную либу sqlcipher
MainActivity.kt           — @AndroidEntryPoint; единственная Activity, Compose host

data/
  local/
    AppDatabase.kt        — @Database v6, DAO-и, MIGRATION_1_2 + … + MIGRATION_5_6
    DbHolder.kt           — ленивое открытие зашифрованной БД (см. §5)
    TransactionDao.kt     — CRUD + keyset-пагинация (по счёту+валюте; за период с фильтрами) + update + all()/forAccount() для экспорта
    StatDao.kt            — инкрементальные суммы (счёт + период + валюта + категория)
    AccountDao.kt         — CRUD справочника счетов (+ deleteAll для импорта-замены)
    CategoryDao.kt        — CRUD справочника категорий (listAll/byName/insert)
  model/
    Currency.kt           — enum валют (code/symbol/displayName)
    AccountEntity.kt      — таблица `accounts` (id, name, color) + палитра из 12 цветов
    CategoryEntity.kt     — таблица `categories` (id, name, isIncome) + палитра цветов для графиков
    TransactionEntity.kt  — таблица `transactions` (с accountId + categoryId + индексы)
    StatEntity.kt         — PeriodType (с ключами и shift) + таблица `stats` (см. §7)
  repository/
    TransactionRepository.kt — бизнес-логика + add/remove/replace + CRUD счетов (см. §6)
    BackupRepository.kt      — экспорт/импорт всей БД в шифрованный контейнер (см. §12)
    CsvExportRepository.kt   — экспорт истории счёта в Excel-CSV (см. §13)
  security/
    PatternLockManager.kt — узор, соль, PBKDF2, счётчик попыток, вайп
  settings/
    SettingsRepository.kt — язык (EN/RU) + currentAccountId в SharedPreferences

di/
  AppModule.kt            — пустой Hilt-модуль (БД ленивая, см. §5)

ui/
  components/PatternLock.kt   — Canvas-виджет 3×3 узора
  locale/AppLocale.kt         — Strings, StringsEn/Ru, LocalStrings, cat(), periodTitle()
  navigation/AppNavGraph.kt   — NavHost: lock → main → settings / accounts / stats
  screens/
    LockScreen.kt             — экран узора
    DashboardScreen.kt        — главный экран + AddDlg (см. §8)
    AccountsScreen.kt         — CRUD-справочник счетов
    SettingsScreen.kt         — выбор языка + экспорт/импорт + CSV + «О приложении» (см. §12–14)
    StatsScreen.kt            — экран «Периоды»: вкладки Статистика/Операции (см. §8.2)
  theme/Theme.kt              — Material3 dark/light палитра
  viewmodel/
    LockViewModel.kt          — старт-ап/вход/вайп
    FinanceViewModel.kt       — состояние дашборда + справочник категорий (см. §8)
    AccountsViewModel.kt      — CRUD счетов, переключение текущего
    SettingsViewModel.kt      — обёртка языка + запуск бэкапа/CSV (busy/message)
    StatsViewModel.kt         — состояние экрана «Периоды»: графики + список операций (см. §8.2)
```

---

## 4. Поток запуска и навигация

1. `FinanceTrackerApp.onCreate()` → `System.loadLibrary("sqlcipher")`
   (обязательно до первого обращения к БД).
2. `MainActivity` инжектит `SettingsRepository`, ставит `AppTheme` и
   `AppNavGraph(settings)`.
3. [`AppNavGraph`](app/src/main/java/com/example/financetracker/ui/navigation/AppNavGraph.kt:17):
   - оборачивает всё в `CompositionLocalProvider(LocalStrings provides stringsFor(lang))`
     — локализация доступна через `LocalStrings.current` в любом Composable;
   - `startDestination = "lock"`;
   - `lock` → при `LockState.Unlocked` навигирует на `main` с `popUpTo("lock")`
     (назад на лок-экран уже нельзя);
   - `main` (Dashboard) → кнопка настроек → `settings` → `popBackStack()`;
   - `main` (Dashboard) → тап по названию счёта в TopAppBar → `accounts`
     → `popBackStack()` (возврат без смены) или `onSwitchAccount` + `popBackStack()`;
   - `main` (Dashboard) → тап по карточке статистики (День/Неделя/Месяц/Год) →
     `stats/{currency}/{periodType}` (валюта дашборда и выбранный тип периода
     передаются аргументами и читаются StatsViewModel через SavedStateHandle —
     каждая карточка открывает свой период, не всегда День) → `popBackStack()`.

Экраны получают ViewModel через `hiltViewModel()`.

---

## 5. Безопасность и открытие БД (сердце проекта)

### PatternLockManager
[`data/security/PatternLockManager.kt`](app/src/main/java/com/example/financetracker/data/security/PatternLockManager.kt)
хранит в SharedPreferences `pattern_prefs`:
- `hash` = SHA-256 от строки `"0-1-4-..."` (индексы точек узора);
- `salt` = Base64 16 случайных байт (генерируется один раз);
- `att` = счётчик неудачных попыток (сбрасывается при успехе).

`derivePassphrase(pattern)` → `PBKDF2WithHmacSHA256(hash, salt, 120000 итераций, 256 бит)`.
Это и есть пароль SQLCipher. `verify()` сравнивает хэш узора.
`shouldWipe()` = `attempts >= 3`. `wipeAll()` чистит prefs и `deleteDatabase("finance.db")`.

### DbHolder
[`data/local/DbHolder.kt`](app/src/main/java/com/example/financetracker/data/local/DbHolder.kt)
— синглтон, держит `AppDatabase?` (по умолчанию `null` = БД «закрыта»).
- `unlock(passphrase)` строит БД с `SupportOpenHelperFactory(passphrase)` и
  пробует реальный запрос (`dao().count()`); неверный пароль SQLCipher бросит
  исключение → возвращает `false`, файл не трогается.
- `recreateWith()` — аварийное удаление файла и создание заново (используется,
  если БД не открывается правильным ключом, напр. наследие старой схемы).
- `db()`/`dao()`/`statDao()` бросают `IllegalStateException`, если БД закрыта.

### LockViewModel
[`ui/viewmodel/LockViewModel.kt`](app/src/main/java/com/example/financetracker/ui/viewmodel/LockViewModel.kt)
— стейт-машина `LockState` (`Setup/Enter/Unlocked/Error/Wiped`).
- Setup: узор ≥4 точек → `save()` → `derivePassphrase` → `unlock() || recreateWith()`.
- Enter: `verify()` → при успехе вывод ключа и `unlock()`; при неудаче
  `shouldWipe()` → `db.lock()` + `plm.wipeAll()` → `Wiped`.
- Тяжёлое (PBKDF2, IO) уводится в `Dispatchers.Default`/`IO`.
- Флаг `setupHint` (`plm.isSet == false`, true при `Wiped`/`reset()`,
  снимается сразу после `save()` в Setup) выбирает мелкую подсказку на
  [`LockScreen`](app/src/main/java/com/example/financetracker/ui/screens/LockScreen.kt):
  `hintSetup` («этим ключом будет зашифрована новая база данных») vs `hintEnter`
  («Введите ключ для входа»).
- **Техническая диагностика (`CrashLog`, `lastError`, стектрейсы,
  «likely SIGSEGV») на экран не выводится** — `db.debugInfo()` читается
  и очищается, детали пишутся в `Log.d("FinTrack", …)`. Пользователь
  видит только локализованный `dbError`.

**Правило:** никогда не создавайте `AppDatabase` напрямую и не обходите
`DbHolder`. Инжектите `DbHolder` и берите DAO через `db.dao()`/`db.statDao()`.

---

## 6. Схема БД и Repository

### Таблицы
`accounts` ([`AccountEntity`](app/src/main/java/com/example/financetracker/data/model/AccountEntity.kt)):
`id` (PK, autoGenerate), `name` (TEXT), `color` (INTEGER, ARGB как Long).
Палитра из 12 фиксированных цветов (`AccountEntity.PALETTE`).
При создании нового счёта `nextColor()` выбирает первый свободный.

`transactions` ([`TransactionEntity`](app/src/main/java/com/example/financetracker/data/model/TransactionEntity.kt)):
`id` (PK, autoGenerate), `accountId` (Int, NOT NULL), `amount`, `currencyCode`,
`categoryId` (Int, NOT NULL — ссылка на `categories.id`), `note`, `isIncome`,
`timestamp`. Индексы `(accountId, currencyCode, id)`, `(categoryId)`,
`(accountId, currencyCode, timestamp, id)` (v5 — keyset-страница периода
на вкладке «Операции») и два индекса v6 для строки фильтров этой же
вкладки: `(accountId, currencyCode, isIncome, timestamp, id)` и
`(accountId, currencyCode, categoryId, timestamp, id)`.

`categories` ([`CategoryEntity`](app/src/main/java/com/example/financetracker/data/model/CategoryEntity.kt)):
`id` (PK, autoGenerate), `name` (TEXT — ключ дефолтной категории из карты
локализации либо введённое пользователем имя как есть), `isIncome` (Boolean —
разделяет списки расходов и доходов). Справочник глобальный (не привязан
к счёту/валюте), порядок = возрастание id (новые добавляются в конец).
Палитра `PALETTE` (16 цветов) и `colorFor(id)` дают детерминированный цвет
столбика графика по id категории.

`stats` ([`StatEntity`](app/src/main/java/com/example/financetracker/data/model/StatEntity.kt)):
PK = составной `(accountId, periodType, periodKey, currencyCode, categoryId)`,
поля `income`, `expense`. `categoryId = AGGREGATE_ID (0)` — агрегирующая
строка периода (итог по валюте без разбивки: читается дашбордом точечно
по PK и используется для границ истории MIN/MAX); `categoryId > 0` —
вклад категории (разбивка для бар-чартов экрана статистики).

### Миграции
[`AppDatabase`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt)
имеет `version = AppDatabase.VERSION` (константа = 6, она же пишется
в заголовок резервных копий):
- `MIGRATION_1_2` — создаёт `stats` (без `accountId`) и заполняет её
  агрегацией из `transactions` по всем периодам.
- `MIGRATION_2_3` — вводит многоучётность:
  1. Создаёт таблицу `accounts`, вставляет дефолтный счёт `id=1`
     (`"Мои финансы"`, первый цвет палитры).
  2. `ALTER TABLE transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1`
     + создание индекса `(accountId, currencyCode, id)`.
  3. Пересоздаёт `stats` с `accountId` в составном PK,
     переносит данные (все со счётом 1) из `transactions`.
- `MIGRATION_3_4` ( [`AppDatabase.MIGRATION_3_4`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt:191) ) — справочник
  категорий и статистика по категориям:
  1. Создаёт `categories` и засевает дефолтные категории: сначала расходные
     (`DEFAULT_EXPENSE`), затем доходные (`DEFAULT_INCOME`) — те же ключи,
     что переводятся картой в `AppLocale`.
  2. Перестраивает таблицу `transactions` (create `transactions_new` +
     `INSERT..SELECT` + drop + rename): колонка `category TEXT NOT NULL`
     из v3 в сущности v4 отсутствует, и `ALTER ADD categoryId` оставил бы
     NOT NULL-колонку без значения — INSERT Room'а падал бы. В новой таблице
     `categoryId` вычисляется подзапросом по (name, isIncome) справочника
     (фолбэк — «Other» того же типа, посеян всегда); старые id сохраняются.
  3. Создаёт индексы на переименованной таблице ровно как объявлены в v4:
     `(accountId, currencyCode, id)` и `(categoryId)` (старые удалены вместе
     со старой таблицей).
  4. Пересоздаёт `stats` с `categoryId` в составном PK и заполняет её
     из уцелевших транзакций: агрегирующие строки `categoryId=0` на период
     и строки по категориям. Ключи агрегации — private data class'ы
     `AggKey`/`CatKey` (Array в HashMap сравнивался бы по ссылке и
     рассыпал дубли).

- `MIGRATION_4_5` ( [`AppDatabase.MIGRATION_4_5`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt:339) ) — только
  `CREATE INDEX (accountId, currencyCode, timestamp, id)` на `transactions`
  для вкладки «Операции» экрана «Периоды»: keyset-страница по диапазону
  timestamp обслуживается индексом без скана истории. Данные не меняются.

- `MIGRATION_5_6` ( [`AppDatabase.MIGRATION_5_6`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt:360) ) — только
  два `CREATE INDEX` на `transactions`: `(accountId, currencyCode, isIncome, timestamp, id)`
  и `(accountId, currencyCode, categoryId, timestamp, id)` — для строки
  фильтров вкладки «Операции» (по типу операции и по категории). Данные не
  меняются; имена индексов совпадают с генерируемыми Room по `@Entity` v6
  (иначе валидация схемы после миграции не совпадёт).

`DbHolder.build()` регистрирует все миграции:
`addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)`
плюс `fallbackToDestructiveMigration()` как страховку.

**Правило добавления новой версии схемы:**
1. Поднимите `version` в `@Database`.
2. Добавьте `MIGRATION_(N-1)_N` с `CREATE TABLE`/`ALTER` и заполнением из данных.
3. Зарегистрируйте её в `DbHolder.build()` через `addMigrations(...)` (можно
   перечислить несколько через запятую).
4. Поднимите `versionCode` в `app/build.gradle.kts`.
Без явной миграции `fallbackToDestructiveMigration` **удалит данные**.

### TransactionRepository
[`data/repository/TransactionRepository.kt`](app/src/main/java/com/example/financetracker/data/repository/TransactionRepository.kt)
— единственная точка работы с БД из UI-слоя. Все методы принимают `acc`
(активный `accountId`) как первый параметр:
- `page(acc, cur, lastId, limit)` — keyset-страница активного счёта и валюты;
- `periodPage(acc, cur, from, to, inc, cat, lastTs, lastId, limit)` — keyset-страница
  записей за период `[from; to)` по паре `(timestamp, id)` (вкладка «Операции»);
  фильтры передаются sentinel'ом `-1` (без фильтра): `inc` — тип (-1/0/1),
  `cat` — `categoryId` или -1;
- `income/expense(acc, cur)` — читают строку `TOTAL` из `stats` (точечно по PK);
- `periodIncome/periodExpense(acc, cur, pt, key)` — сумма за конкретный период;
- `add(t)`/`remove(t)`/`replace(old, new)`/`wipe()` — обёрнуты в `db.db().withTransaction { }`
  (Room из `room-ktx`), внутри которых вставка/удаление/замена транзакции И
  дельта-обновление `stats` (`applyDelta`) атомарны; `replace` снимает вклад
  старой строки (`sign = -1`), выполняет `dao.update(new)` и прибавляет вклад
  новой (`sign = +1`) — корректно даже при переезде записи в другой период,
  категорию или тип;
- `applyDelta(t, sign)` — для каждого `PeriodType` делает `insertIfAbsent`
  (создаёт нулевую строку периода по `accountId` при необходимости) и `addDelta`
  (`UPDATE ... SET income = income + :inc ... WHERE accountId = ...`).
  `sign = +1` при добавлении, `-1` при удалении.
- **CRUD счетов**: `addAccount(name)` — вставляет в `accounts` с авто-цветом;
  `renameAccount(id, name)` — переименование; `deleteAccount(id)` — каскад
  (transactions + stats + accounts) в `withTransaction`;
  `listAccounts()` — список всех; `accountCount()` — количество.
- **CRUD категорий**: `listCategories()` — весь справочник по возрастанию id
  (= порядок добавления); `addCategory(name, isIncome)` — trim + проверка на
  дубль того же типа регистронезависимо (`byName ... COLLATE NOCASE`),
  при совпадении возвращает id существующей, иначе вставляет новую и
  возвращает её id (null при пустом имени); `ensureDefaultCategories()` —
  засевает дефолтный набор на свежесозданной БД (миграции не запускаются,
  когда файл создаётся сразу v4).
- **Для экрана статистики**: `minPeriodKey`/`maxPeriodKey(acc, cur, pt)` —
  границы истории по агрегирующим строкам (MIN/MAX periodKey; формат ключей
  гарантирует, что лексикографический порядок совпадает с хронологическим),
  `periodByCategory` — разбивка сумм по категориям за конкретный период
  (`List<CategorySum>`).

---

## 7. Статистика и периоды

[`PeriodType`](app/src/main/java/com/example/financetracker/data/model/StatEntity.kt:15):
`TOTAL` (ключ `""`), `DAY` (`yyyy-MM-dd`), `WEEK` (`yyyy-Www`, ISO), `MONTH`
(`yyyy-MM`), `YEAR` (`yyyy`). Каждый период вычисляет ключ из `timestamp`
в системной таймзоне (`dateOf()`). Формат выбран так, что строковое
сравнение ключей совпадает с хронологическим (это используют MIN/MAX
границ истории).

Для навигации по периодам у каждого типа есть:
`startDateOf(key)` — первый день периода (ISO-неделя через 4 января
+ weekOfWeekBasedYear), `shift(key, delta)` — сдвиг ключа на delta
периодов (день/неделя/месяц/год), `currentKey()` — ключ текущего периода.
Используется экраном статистики для листания «назад/вперёд» до границ БД.

Вклад транзакции идёт ровно в один ключ каждого периода её валюты —
в ДВЕ строки: агрегирующую (`categoryId = 0`, читается дашбордом точечно)
и строку категории (для разбивки). Итог по валюте за всё время = строка
`TOTAL`. Суммы за день/неделю/месяц/год на дашборде = точечные запросы по
текущим ключам из агрегирующих строк. Никаких `SUM()` по всей таблице
`transactions`.

---

## 8. Дашборд: ViewModel + Screen

### FinanceViewModel
[`ui/viewmodel/FinanceViewModel.kt`](app/src/main/java/com/example/financetracker/ui/viewmodel/FinanceViewModel.kt)
— `UiState` (income, expense, balance, currency, items, periods, loading,
loadingMore, hasMore) в `StateFlow`. `PAGE_SIZE = 20`.
Дополнительно: `account: StateFlow<AccountEntity?>` — текущий счёт.
- `loadAccount()` — читает `currentAccountId` из `SettingsRepository`,
  подгружает `AccountEntity` из `repo.listAccounts()`.
- `switchAccount(id)` / `setAccount(id)` — переключает активный счёт через
  `SettingsRepository.setCurrentAccount(id)` + `reload()`.
- `reload()` — `loadAccount()` → первая страница
  (`repo.page(acc, cur, 0, 20)`) + `refreshTotals()`.
- `loadMore()` — следующая страница от `items.last().id`, с защитой от гонок.
- `refreshTotals()` → `refreshPeriods()` — перечитывает TOTAL и 4 периода
  (все запросы к `stats` по `accountId + currencyCode`).
- `categories: StateFlow<List<CategoryEntity>>` — справочник категорий,
  загружается в `loadAccount()` (там же `ensureDefaultCategories()`);
  `addCategory(name, isIncome): Int?` — создаёт категорию и обновляет
  справочник (используется кнопкой «+ Добавить новую» в AddDlg).
- `add(amount, categoryId, note, income, timestamp)` — пишет в БД (с
  `accountId` из `SettingsRepository` и выбранной датой записи),
  затем **вставляет запись в начало окна без перечитывания**.
- `remove(t)` — удаляет, фильтрует из окна, при опустошении окна перечитывает.
- `replace(old, new)` — `repo.replace` (withTransaction + дельты `stats`),
  затем **map-замена** строки в окне по id (сортировка окна по id,
  позиция записи не меняется — без перечитывания БД) + `refreshTotals()`.
- Все обращения к БД в `try/catch`; ошибка → безопасное пустое состояние.

### AccountsViewModel
[`ui/viewmodel/AccountsViewModel.kt`](app/src/main/java/com/example/financetracker/ui/viewmodel/AccountsViewModel.kt)
— `accounts: StateFlow<List<AccountEntity>>`, `error: StateFlow<String?>`.
- `add(name)` — валидация (не пусто), `repo.addAccount()`, `refresh()`.
- `rename(id, name)` — валидация, `repo.renameAccount()`.
- `delete(id)` — валидация: нельзя удалить последний счёт
  (`errLastAccount`) или текущий (`errCurrentAccount`);
  при успехе — `repo.deleteAccount(id)` (каскад).
- `select(id)` — `settings.setCurrentAccount(id)`.
- `clearError()` — сброс кода ошибки.

### DashboardScreen
[`ui/screens/DashboardScreen.kt`](app/src/main/java/com/example/financetracker/ui/screens/DashboardScreen.kt)
признаки: `vm: FinanceViewModel`, `onOpenSettings`, `onOpenAccounts`.
структура Column:
1. Карточка баланса — содержит заголовок «Баланс» и кнопку `IconButton`
   (`ExpandMore`/`ExpandLess`) для сворачивания/разворачивания.
   Вертикальные внутренние отступы сжаты (10.dp по вертикали, 20.dp
   горизонтально), зазор между суммой и строкой доход/расход — 4.dp
   (экономия высоты в обоих состояниях). Состояние
   хранится В FinanceViewModel как `Map<accountId, Boolean>`
   (`balanceExpandedMap`/`toggleBalance`) — переживает
   переходы на Настройки/Статистику/Счёта в рамках сессии, но не сохраняется
   между запусками (после выхода из приложения баланс снова скрыт). В
   свернутом состоянии (по умолчанию, для счёта без записи в карте — false)
   виден только заголовок; в развёрнутом — сумма баланса (белый символ
   валюты через `amountStr()`,
   число `IncomeGreen` при `≥ 0`, `error` при `< 0`), строка дохода/расхода.
2. Компактная статистика (`Row` из карточек `weight(1f)`) — чистая сумма за
   `DAY/WEEK/MONTH/YEAR`, формат через `compact()`, цвет `IncomeGreen`/
   `error` по знаку. Показывается **только** когда `balanceExpanded = true`
   (скрывается вместе со сворачиванием баланса).
3. `LazyColumn` с keyset-ленивой загрузкой: `LaunchedEffect` + `snapshotFlow`
   по `LazyListState` триггерит `vm.loadMore()` за 3 элемента до конца;
   внизу спиннер при `loadingMore`; пустое состояние `s.noRecords` с
   подстановкой `{CURRENCY}` (см. §10). Категория в карточке отображается
   через `catById[categoryId]` (имена берёт из справочника БД), дата —
   из `timestamp` записи (в т.ч. изменённая пользователем).
4. FAB → `AddDlg` (доход/расход, сумма, категория, заметка) — см. §8.1.

**TopAppBar**: вместо статического `s.appTitle` — название текущего счёта
(`acc?.name ?: s.appTitle`) в виде **тональной кнопки-пилюли**
(`RoundedCornerShape(percent = 50)`): полупрозрачный фон
`Color(acc.color).copy(alpha = 0.15f)`, обводка 1.dp в цвет счёта,
ведущая точка-индикатор цвета счёта, `maxLines = 1` с ellipsis,
trailing-иконка `ArrowDropDown`; `clickable(role = Role.Button,
onClickLabel = s.changeAccount)` — тап открывает `onOpenAccounts()`.
`onClickLabel` — новая строка в `Strings` (`changeAccount`: EN «Change
account» / RU «Сменить счёт»). В `actions` — выбор валюты: `TextButton`
с подписью `code symbol` и trailing-иконкой `ArrowDropDown` (как у
кнопки счёта), открывающий `DropdownMenu` со всеми `Currency.entries`,
затем шестерёнка настроек. Все операции
(баланс, история, статистика, добавление, удаление) идут только с
текущим `accountId` из `SettingsRepository`.

Диалоги (в конце тела Composable, поверх Scaffold):
- `viewed` — просмотр комментария записи по тапу на карточку.
- `toDelete` — подтверждение удаления (`AlertDialog` с категорией и суммой).
- `edit` — редактирование записи: тот же `AddDlg` с `initial` (поля
  предзаполнены, заголовок `s.edit` «Поменять», кнопка `s.save`
  «Сохранить»); сохранение — `vm.replace(old, new)` с неизменными id и
  валютой.
- `menuFor` — id записи с открытым меню «три точки» (`Icons.Default.MoreVert`
  вместо прежней кнопки удаления): `DropdownMenu` с пунктами «Поменять»
  (`s.edit` → открывает `edit`) и «Удалить» (`s.delete` → открывает
  `toDelete`). Одна переменная на весь список — открытым может быть
  только одно меню. Аналогичное меню на карточках вкладки «Операции».
- `BackHandler` отключён, пока открыт любой диалог, меню или FAB-окно.

**Карточки компактной статистики — кнопки**: каждая (`День/Неделя/Месяц/Год`)
окружена `Modifier.clickable(role = Role.Button, onClickLabel = s.statsTitle)`
и ведёт на экран `stats` с СВОИМ периодом (`onOpenStats(st.type)` →
`stats/{currency}/{periodType}`). Layout — двухстрочный (в узкой колонке
`weight(1f)` всё в один ряд не помещается и «съезжает»):
строка 1 — подпись периода слева + `ChevronRight` у правого края
(`SpaceBetween`); строка 2 — иконка `BarChart` и `compact(net)`
(ellipsis по длине суммы). Сумма — `labelMedium` Bold (`bodyMedium`
не влезал по ширине в узкую колонку), горизонтальные отступы Column
карточки — 6.dp. ripple/onClickLabel добавлены через
clip+clickable поверх Card.

### 8.1 AddDlg (диалог добавления/редактирования записи)
[`AddDlg`](app/src/main/java/com/example/financetracker/ui/screens/DashboardScreen.kt:388)
— `AlertDialog` с параметрами `currency`, `categories` (справочник из БД),
`initial: TransactionEntity?`, `onCreateCategory(name, isIncome): Int?`,
`ok(amount, categoryId, note, isIncome, timestamp)`. `initial == null` —
создание; `initial != null` — редактирование: сумма/заметка/тип/категория/
дата предзаполнены значениями записи, заголовок `s.edit`, кнопка
`ok`-подписи `s.save` (вместо `s.add`); вызывается одинаково с дашборда
(`vm.replace`) и с вкладки «Операции» (`vm.edit`). Внутри:
- **Сумма**: `OutlinedTextField` с `KeyboardOptions(keyboardType = Decimal)`
  и фильтром ввода (цифры + запятая/точка); парсинг `replace(',', '.')`,
  невалидный ввод подсвечивается `isError`; кнопка «Добавить» неактивна,
  пока сумма не положительна или не выбрана категория.
- **Тип** (Расход/Доход) — чипы `FilterChip` (подписи `labelSmall`),
  **справа в той же строке** — `FilledTonalButton` с иконкой
  `CalendarMonth` (14.dp) и текущими датой/временем записи: подпись
  `dd.MM\nHH:mm` в ДВЕ строки по центру (`labelSmall`,
  `TextAlign.Center`, `maxLines = 2`), `contentPadding` 8.dp,
  отступ слева 8.dp — одной строкой кнопка не влезала в ширину
  диалога и упиралась в край. Тап открывает
  `DatePickerDialog` (Material3, UTC-конвертация `initialSelectedDateMillis`
  и обратно), после ОК сразу — диалог с `TimePicker` (`rememberTimePickerState`,
  24ч). Результат пикеров — `ts`, уходит в `ok` и далее в `timestamp`.
  Даты в будущем разрешены (планирование).
- **Категория**: `ExposedDropdownMenuBox` по отфильтрованному `catsFor`
  (справочник БД по `isIncome` выбранного типа, порядок id). В конце списка
  — пункт «Добавить новую» (`Icons.Default.Add` + `s.addNewCategory` — плюс
  в текст строки НЕ включён, чтобы не дублировать иконку):
  открывает вложенный `AlertDialog` с полем названия (по одному в строке)
  и кнопками ОК (`s.ok`) / Отмена (`s.cancel`); ОК создаёт категорию через
  `onCreateCategory`, выбирает её (`catId = id`) и добавляет в конец списка.
  Дубликат (NOCASE) выбирает существующую (см. репозиторий §6).
  При смене типа выбранная категория, не принадлежащая новому типу,
  откатывается на первую подходящую (`effectiveCat`).
- `note` — однострочное поле (как было).

### 8.2 StatsScreen (экран «Периоды»)
[`StatsScreen`](app/src/main/java/com/example/financetracker/ui/screens/StatsScreen.kt:47)
+ [`StatsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/StatsViewModel.kt:78):
структура Column (вертикальные отступы сжаты ради экономии высоты:
горизонтальный внешний 12.dp, `Spacer` между блоками 4–8.dp,
между карточками графиков 8.dp, padding карточки 12/10.dp):
1. `TopAppBar` с кнопкой «Назад» (`Icons.AutoMirrored.Filled.ArrowBack` →
   `popBackStack()`) и заголовком `s.periodsTitle` («Периоды»/«Periods»;
   `s.statsTitle` осталась для подсказки карточек дашборда).
2. Строка `FilterChip`: День/Неделя/Месяц/Год (`vm.setType`) — четыре чипа
   в ряд по `weight(1f)`, `spacedBy(4.dp)`; подписи — `labelSmall`, одна
   строка (`maxLines=1`, `overflow=Clip`) — стандартный `labelMedium`
   не влезает в четверть ширины для длинных слов («Неделя», «Месяц»).
3. Строка периода: `IconButton ChevronLeft` | заголовок периода
   (`s.periodTitle(type, key)` — см. §9) | `IconButton ChevronRight`.
   Кнопки листания (`vm.shift(±1)`) отключаются у границ истории
   (`minKey`/`maxKey` из `stats`, `canGoBack/canGoForward` в состоянии);
   стартовый ключ = текущий период, но не вне границ.
4. `SingleChoiceSegmentedButtonRow` — компактные вкладки «Статистика»/
   «Операции» (`vm.setTab`, enum `StatsTab`). Выбранная вкладка живёт в
   ViewModel: переживает смену типа периода и листание, сбрасывается на
   «Статистика» только при выходе на дашборд (VM уничтожается).
5. Область контента `weight(1f)` (никакого `verticalScroll` вокруг
   `LazyColumn` — иначе ленивость теряется):
   - **«Статистика»**: прокручиваемая `verticalScroll`-колонка с двумя
     `BarChartCard` (Расходы сверху — красный, Доходы снизу — зелёный):
     строка заголовка + «Всего» за период (реальные итоги из агрегирующей
     строки `stats`, не искажённые топ-N), ниже — столбики: строка 1 —
     название категории слева + сумма справа (`SpaceBetween`), строка 2 —
     бар на всю ширину (полоска шириной `value/max`, скругление, фон track
     `colorScheme.surface`), не больше `TOP_N = 7` категорий с наибольшим
     значением, отсортированы по убыванию; подпись = категория (`s.cat(name)`),
     цвет = `CategoryEntity.colorFor(id)`. Пустой период → `s.noStatsData`.
     Рисование чистым Compose, без сторонних библиотек (офлайн-политика).
   - **«Операции»**: над списком — **тонкая строка фильтров** (`Row`):
     слева `TextButton` + стрелка `Icons.Default.ArrowDropDown` (как
     выбор валюты на дашборде) открывает `DropdownMenu` типа операции:
     «Все операции» (`s.allOps`, по умолчанию) / «Расходы» (`s.expenses`) /
     «Доходы» (`s.income`); при выбранном типе справа появляется второй
     `DropdownMenu` категорий этого типа — «Все категории»
     (`s.allCategories`) + `st.cats.filter { it.isIncome == … }`. Значения
     живут в `StatsState.opsType/opsCat` (sentinel `-1` = без фильтра);
     смена — сброс окна + `loadOps(reset = true)`. Затем `LazyColumn`
     записей выбранного периода (активный счёт
     + валюта) с keyset-ленивой подгрузкой по 20 (как дашборд):
     `snapshotFlow` по `LazyListState` триггерит `vm.loadOpsMore()` за
     3 элемента до конца; карточка — категория, дата `dd.MM HH:mm`, сумма
     со знаком, кнопка «три точки» (меню «Поменять»/«Удалить», как на
     дашборде); тап по карточке — диалог заметки (`viewed`).
     Удаление — подтверждение `AlertDialog` (`toDelete`, категория+сумма) →
     `vm.remove(t)`: `repo.remove` (транзакция + дельта `stats`), изъятие
     из окна с дозагрузкой от курсора, затем `loadBars()` — графики и итоги
     перечитываются из `stats`. «Поменять» — `AddDlg(initial = t)`,
     сохранение → `vm.edit(...)` (см. §8.3). Пустой период → `s.noStatsData`.

### 8.3 Прочие ViewModel
[`StatsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/StatsViewModel.kt:78)
— `state: StateFlow<StatsState>` (type, key, minKey, maxKey, currency,
expenseBars, incomeBars, totalExpense, totalIncome, loading, empty,
tab, catNames, cats, opsType/opsCat (фильтры «Операций», `-1` = все),
ops, opsLoaded/opsLoading/opsLoadingMore/opsHasMore,
opsCursorTs/opsCursorId). `setOpsType`/`setOpsCat` — сброс окна и
`loadOps(reset = true)` (смена типа также сбрасывает фильтр категории на
«Все категории»); `edit(old, ...)` — `repo.replace` + `loadOps(reset = true)`
+ `loadBars()`; `addCategory` — обновление `cats`/`catNames` (для фильтра и
формы редактирования). Валюта и тип периода активны те же, что на
дашборде — приходят навигационными аргументами
`stats/{currency}/{periodType}` из `SavedStateHandle` (`periodType` —
имя константы `PeriodType`, парсится `valueOf` с фолбэком на DAY).
`setType`/`shift` синхронно сбрасывают окно операций (новый период —
новый список); `loadOps` читает `repo.periodPage` (с `opsType`/`opsCat`) для диапазона
`[начало; начало следующего)` периода (`periodRange()` через
`startDateOf`/`shift` в системной зоне); курсор keyset — последняя
загруженная пара (timestamp, id). Все обращения к БД в `try/catch`
с безопасным пустым состоянием.

### AccountsScreen
[`ui/screens/AccountsScreen.kt`](app/src/main/java/com/example/financetracker/ui/screens/AccountsScreen.kt)
— CRUD-справочник счетов:
- `LazyColumn` карточек: цветовой индикатор (кружок), название, бейдж
  «Текущий» для активного. Тап по карточке (не-текущей) →
  `vm.select(id)` + `onSwitchAccount(id)` + `onBack()`.
- Кнопки на каждой карточке: «Переименовать» (всегда), «Удалить»
  (только если не текущий и не единственный).
- FAB «Добавить» → диалог с полем названия.
- Ошибки (валldation, БД) показываются через `Snackbar`.

Хелперы в `DashboardScreen.kt`: `fmt()` (полный формат с валютой, для диалогов),
`amountStr()` (AnnotatedString: число `numColor`, символ валюты `Color.White`;
параметр `withSymbol` включает/выключает символ), `IncomeGreen`
(`Color(0xFF81C784)` — цвет положительных сумм), `periodLabel()`, `compact()`.

---

## 9. Локализация

[`ui/locale/AppLocale.kt`](app/src/main/java/com/example/financetracker/ui/locale/AppLocale.kt)
— `data class Strings` со всеми строками; два экземпляра `StringsEn` и
`StringsRu`; `LocalStrings` (`compositionLocalOf { StringsEn }`);
`stringsFor(lang)`; `Strings.cat(name)` — перевод категории из `categories`
(для дефолтных ключей; пользовательские имена возвращаются как есть —
перевести динамические данные нельзя, это ожидаемое поведение).
Поле `langCode` ("en"/"ru") хранится в `Strings` и используется
`Strings.periodTitle(pt, key)` — человекочитаемый заголовок периода для
экрана статистики: `DAY` → «25 сентября 2026» (d MMMM yyyy), `WEEK` →
«21.09.2026-27.09.2026», `MONTH` → «Сентябрь 2026» (MMMM yyyy), `YEAR` →
«2026 год» / «2026». Названия месяцев/дней форматирует `java.time` по
`Locale(langCode)`, поэтому они не дублируются в `Strings`.

**Правило:** любая новая строка UI добавляется в поле `Strings` И в оба
экземпляра (En и Ru). Категории хранятся в `catsEn`/`catsRu` по ключам
("Food", "Salary" и т.п.) — эти же ключи пишутся в БД (`category`).
`Language` переключается в `SettingsRepository` (SharedPreferences `settings`).
`res/values/strings.xml` содержит только `app_name` — весь остальной текст идёт
через `LocalStrings`, не через Android-ресурсы.

---

## 10. Как выполнять типовые задачи

**Новая строка UI:** добавить поле в `Strings` → значения в `StringsEn` и
`StringsRu` → использовать `LocalStrings.current.<поле>`.

**Новая валюта:** добавить константу в [`Currency`](app/src/main/java/com/example/financetracker/data/model/Currency.kt)
(`code`, `symbol`, `displayName`). Смена валюты — `vm.setCurrency()`; список и
статистика автоматически фильтруются по `code`. Миграции БД не нужны.

**Новая категория:** пользовательские категории создаются из `AddDlg`
(пункт «+ Добавить новую») и хранятся в таблице `categories`
(`TransactionRepository.addCategory`). Чтобы добавить **дефолтную**
категорию (общую для всех установок) — добавить ключ+перевод в `catsEn`
и `catsRu` и в `CategoryEntity.DEFAULT_EXPENSE`/`DEFAULT_INCOME`
(используются миграцией v4 и `ensureDefaultCategories()`).

**Новый экран:** Composable в `ui/screens/` + ViewModel (если нужен) в
`ui/viewmodel/` + `composable("route")` в `AppNavGraph`.

**Новый счёт:** создаётся через `AccountsScreen` (FAB «Добавить»).
`AccountEntity` имеет только `id`, `name`, `color`. Цвет выбирается
автоматически из палитры `AccountEntity.PALETTE` (12 цветов,
`nextColor()` берёт первый свободный). Все `transactions` и `stats`
привязаны к `accountId` — новые данные идут только в текущий счёт.
Удаление счёта — каскадное (transactions + stats + accounts).
Ограничения: нельзя удалить последний, нельзя удалить текущий.

**Новое поле в транзакции:** добавить в `TransactionEntity` → поднять `version`
в `AppDatabase` → написать `MIGRATION` с `ALTER TABLE ADD COLUMN` (с дефолтом)
→ зарегистрировать в `DbHolder` → обновить DAO/Repository/ViewModel/Screen →
поднять `versionCode`.

**Новый период статистики:** добавить вариант в `PeriodType` с `keyOf()`;
`applyDelta` и `refreshPeriods` перебирают `PeriodType.entries` автоматически.
Для миграции существующих данных — пересобрать `MIGRATION`/новую миграцию,
заполняющую новый тип.

---

## 11. Чек-лист перед push (обязательно)

- [ ] Сигнатуры совпадают: DAO ↔ Repository ↔ ViewModel ↔ Screen.
- [ ] Нет «висящих» вызовов удалённых методов (напр. старый `getAll()`).
- [ ] Импорты добавлены/убраны, скобки замкнуты.
- [ ] Изменение схемы → есть миграция + `version` поднят + `addMigrations`.
- [ ] `versionCode` поднят при релизе.
- [ ] Новые строки — в оба языка.
- [ ] Много-табличные операции — в `withTransaction`.
- [ ] Локальный `gradlew` НЕ запускался (только GitHub CI).

---

## 12. Экспорт/импорт резервных копий

Реализован в [`BackupRepository`](app/src/main/java/com/example/financetracker/data/repository/BackupRepository.kt),
UI-вход — раздел «Данные» в [`SettingsScreen`](app/src/main/java/com/example/financetracker/ui/screens/SettingsScreen.kt:32),
логика запуска/состояний — в [`SettingsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/SettingsViewModel.kt:25).
После успешного импорта дашборд принудительно перечитывается
(`onDataChanged` → `financeVm.reload()` в `AppNavGraph`): инкрементальные
состояния ViewModel невалидны после изменения всего набора данных.

### Формат контейнера
`FINTX1` (magic, 6 Б) + salt (16 Б) + IV (12 Б) + AES-256-GCM(gzip(JSON)).
JSON: `format`, `dbVersion` (схема на момент дампа), `createdAt`, массивы
`accounts`, `categories`, `transactions` (поля — 1:1 с сущностями, id сохраняются).
`stats` в файл **не пишется** — пересобирается при импорте (см. ниже), что
гарантирует консистентность сумм по [`AGENTS.md`](AGENTS.md).

### Криптография
- Ключ = `PBKDF2WithHmacSHA256(пароль юзера, salt, 310 000 итераций, 256 бит)` —
  **независим от графического узора**: файл восстанавливается на любом устройстве.
- Шифрование — `AES/GCM/NoPadding` (128-битный тег): подделка/повреждение
  обнаруживаются; неверный пароль → `AEADBadTagException` →
  `BackupWrongPasswordException` (тег служит проверкой пароля).
- Salt и IV — `SecureRandom` на каждый экспорт; байты ключа обнуляются
  после использования (`key.fill(0)`).
- Файл передаётся через SAF (`CreateDocument`/`OpenDocument`, mime
  `application/octet-stream`, расширение `.fintx`) — разрешений не требуется,
  офлайн-политика соблюдается.

### Импорт — два режима ([`ImportMode`](app/src/main/java/com/example/financetracker/data/repository/BackupRepository.kt:34))
Оба выполняются в одной `db().withTransaction { }`:
- **REPLACE** — очистка всех четырёх таблиц, вставка с исходными id
  (keyset-пагинация и история целы), пересборка `stats` дельтами
  `addStats` по каждой транзакции, затем `fixCurrentAccount()` (сохранённый
  `currentAccountId` мог указать на несуществующий счёт).
- **MERGE** — счета матчатся по имени (NOCASE), категории — по
  (имя, тип), транзакции дедуплицируются по сигнатуре содержимого
  (`accountId|currency|amount|category|isIncome|timestamp|note` уже после
  маппинга id); новые получают локальные id, вклад в `stats` добавляется дельтой.

Пароль на импорте — один ввод + выбор режима в `AlertDialog` (деструктивная
замена — только через подтверждение, по правилам UI). В обоих режимах после
импорта вызывается `repo.ensureDefaultCategories()` (дамп мог не содержать категорий).

### UI
Диалог пароля экспорта — ввод дважды (мин. 8 символов, совпадение). На время
операции (`busy`) экран перекрывается затемнением с `CircularProgressIndicator`
(тяжёлые шаги — PBKDF2/gzip/IO — идут на `Dispatchers.IO`). Результат —
Snackbar с числом записей; типы ошибок различаются: неверный пароль,
не-копия/битый файл (`BackupFormatException`), копия из будущей версии
(`BackupNewerVersionException`). Сбои не роняют процесс. Все строки —
в `StringsEn`/`StringsRu` (`dataSection`, `exportTitle`, `importMode*`, …).

---

## 13. Экспорт истории счёта в CSV

Реализован в [`CsvExportRepository`](app/src/main/java/com/example/financetracker/data/repository/CsvExportRepository.kt:55)
— «только чтение»: в файл попадает вся история выбранного счёта (все валюты).
Пароль/шифрование не применяются — CSV для Excel/1С, не для восстановления.

- **Формат (Excel-совместимый)**: разделитель `;`, десятичная **запятая**,
  UTF-8 с **BOM**, CRLF. Экранирование по RFC: ячейка в кавычки при `;`/`"`/
  переводе строки (переводы строк в комментарии заменяются пробелом). Файл
  открывается двойным кликом в Excel/LibreOffice/1С на русской локали без
  мастера импорта.
- **Колонки** (8, заголовки локализованы через `CsvLabels`, собираемый из
  `Strings` в ViewModel): Дата `yyyy-MM-dd`, Время `HH:mm`, Тип
  (`incomeChip`/`expenseChip`), Категория — [`Strings.cat()`](app/src/main/java/com/example/financetracker/ui/locale/AppLocale.kt:332)
  по имени из справочника (`categoryId → name`), Приход, Расход (одна из
  двух ячеек пустая), Валюта, Комментарий.
- **Итоги**: после группы каждой валюты — строка «Итого» с суммами приходов
  и расходов. Смешивать валюты в один итог нельзя — расчёт строго в
  пределах кода валюты, строки группируются по `currencyCode`.
- **Имя файла**: `<счёт>-yyyy-MM-dd_HH-mm.csv` ([`suggestFileName()`](app/src/main/java/com/example/financetracker/data/repository/CsvExportRepository.kt:70));
  запрещённые в ФС символы (`\/:*?"<>|`), пробелы и control-символы → `_`.
- **Поток UI**: карточка «Экспорт в CSV» в разделе «Данные» [`SettingsScreen`](app/src/main/java/com/example/financetracker/ui/screens/SettingsScreen.kt:34)
  → `AlertDialog` выбора счёта (по умолчанию текущий, `vm.refreshAccounts()`)
  → SAF `CreateDocument("text/csv")` → спиннер `busy` + Snackbar с числом
  строк (`csvDone`/`csvErr`). При недоступности БД пустой список — безопасно
  `csvNoAccounts`.
- Чтение: [`TransactionDao.forAccount(acc)`](app/src/main/java/com/example/financetracker/data/local/TransactionDao.kt:51)
  — история счёта в хронологическом порядке (`ORDER BY timestamp, id`);
  БД не меняется, `withTransaction` не требуется.
- Строки `csvTitle/csvDesc/csvPickAccount/csvNoAccounts/csvDate/csvTime/
  csvType/csvIncome/csvExpense/csvCurrency/csvTotal/csvDone/csvErr` — в оба
  языка (`StringsEn`/`StringsRu`).

---

## 14. «О приложении»

В конце [`SettingsScreen`](app/src/main/java/com/example/financetracker/ui/screens/SettingsScreen.kt:34) — раздел `s.aboutTitle`:
- строка `s.versionLabel + BuildConfig.VERSION_NAME` (значение берётся из
  `app/build.gradle.kts`, в коде не дублируется);
- кликабельная строка `s.developerSite` → `Intent.ACTION_VIEW` на
  `https://github.com/pikselvorkeraund/fintrack`. Разрешения INTERNET у
  приложения нет — открытие делегируется браузеру системы, офлайн-политика
  соблюдается; отсутствие обработчика (браузер отключён) перехватывается
  `try/catch` и игнорируется.
- Строки `aboutTitle/versionLabel/developerSite` — в оба языка.

---

## 15. Известные нюансы

- **Экран статистики и пустой ключ периода**: `StatsState` при создании имеет
  `key = ""` (до первой загрузки из БД). `periodTitle()`, `canGoBack`/
  `canGoForward` и `shift()` обязаны корректно обрабатывать пустой ключ —
  `PeriodType.shift("")`/парсинг ключа бросают исключение и роняют приложение
  на первой же композиции (краш по клику на карточку дашборда). Поэтому:
  пустой key → false для кнопок/пустой заголовок, стартовое состояние —
  `loading = true`, `shift()` с пустым ключом — no-op.

- **Ключ периода обязан всегда соответствовать типу**: ключи разных типов
  имеют разный формат (`yyyy-MM-dd`, `yyyy-Www`, …), и `PeriodType.shift()/
  startDateOf()` упадут, если передать ключ не своего типа (напр. DAY-ключ
  при WEEK → `split("-W")` бросает). Сценарии, где это могло произойти,
  и их защита в [`StatsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/StatsViewModel.kt):
  - `setType()` меняет `type`, но корутина `load()` обновляет ключ «потом» —
    первая перерисовка с новым типом и старым ключом = краш. Решение:
    `setType()` **синхронно** сбрасывает `key=""`, `minKey/maxKey=null`,
    графики и `loading=true`;
  - гонка `load()`: старая корутина заканчивает запрос после `setType()` и
    накладывает ключ своего (уже неактуального) типа. Решение: проверка
    `_state.value.type != pt → return@launch` перед записью границ и ключа;
  - гонка `loadBars()`: результат запроса перестал актуален после
    `setType()`/`shift()`. Решение: в начале — `key.isEmpty() →
    return@launch`; в конце — сверка `type` и `key` с зафиксированными
    на старте `st0` (`type != st0.type || key != st0.key → return@launch`).

- **Вкладка «Операции»: курсор и окно.** Keyset-курсор по периоду
  (`opsCursorTs/opsCursorId`) — последняя ЗАГРУЖЕННАЯ пара, а не последний
  элемент окна: после удаления записи из середины окна дозагрузка от
  прежнего курсора не должна возвращать уже показанные записи. Курсор
  сбрасывается только при `reset` (смена типа/периода). `catNames`
  (id → имя) подгружается вместе с графиками в `loadBars()`, поэтому
  вкладка корректна и при первом переходе на неё.
- **Редактирование записи на «Операциях» перезагружает страницу с нуля.**
  Окно сортировано по `timestamp` и фильтровано (`opsType`/`opsCat`) —
  после `vm.edit` запись могла сместиться относительно сортировки или
  выпасть из фильтрованной выборки, поэтому map-замена в окне некорректна;
  `loadOps(reset = true)` всегда даёт консистентный список. На дашборде
  (окно по `id`, без фильтра) вместо этого — map-замена без перечитывания.
- **Цвет счёта из БД конвертировать только через `Color(Long.toInt())`**, а не
  `Color(long.toULong())`. `AccountEntity.color` хранит `0xAARRGGBB` как `Long`;
  первичный value-конструктор `Color(ULong)` трактует число как внутреннее
  представление (в старших битах — индекс цветового пространства) и при рендере
  падает с `ArrayIndexOutOfBoundsException: length=18; index=18` внутри
  `androidx.compose.ui.graphics.Color.getColorSpace`. `Color(Int)` корректно
  декодирует ARGB в sRGB.
- Room invalidation-трекер с SQLCipher может не срабатывать, поэтому
  `FinanceViewModel` после записи обновляет состояние **явно**
  (`reload()`/`refreshTotals()`), а не полагается на `Flow`.
- `@Insert(onConflict = REPLACE)` у `insert()` возвращает `Long` id —
  используется для вставки в начало окна.
- `StatDao.insertIfAbsent` (`IGNORE`) + `addDelta` (`UPDATE`) — паттерн
  «upsert дельтой»: создаёт нулевую строку периода, затем прибавляет дельту.
- **`CrashLog` остаётся инфраструктурой диагностики, но не UI**: контрольные
  точки пишутся как раньше, при следующем запуске `LockViewModel.init` читает
  `db.debugInfo()` (это же очищает файлы) и пишет содержимое в logcat.
  Экран блокировки показывает только `s.dbError` — сообщение вида
  `Error: no java crash — likely SIGSEGV` пользователю не демонстрируется.
- `enableBackup="false"` в манифесте — резервное копирование отключено
  осознанно (шифрованная БД + приватность).
- `bundle { language { enableSplit = false } }` — обе локализации в одном APK.

---

## 16. Актуальность документации

**Этот файл необходимо держать в актуальном состоянии при заметных изменениях
проекта.** Любая существенная правка — новая сущность/миграция, изменение
архитектуры слоёв (DAO/Repository/ViewModel/Screen), новый экран или маршрут,
смена схемы БД, изменение правил безопасности или производительности, добавление
типовых операций — обязана сопровождаться обновлением соответствующего раздела
[`DEV.md`](DEV.md). Перед push (см. чек-лист §11) убедитесь, что описание в
DEV.md отражает фактическое состояние кода: устаревшая документация хуже, чем
её отсутствие.