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
- `versionCode`/`versionName` — в [`app/build.gradle.kts`](app/build.gradle.kts:15)

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
    AppDatabase.kt        — @Database v4, DAO-и, MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4
    DbHolder.kt           — ленивое открытие зашифрованной БД (см. §5)
    TransactionDao.kt     — CRUD + keyset-пагинация (по счёту + валюте)
    StatDao.kt            — инкрементальные суммы (счёт + период + валюта + категория)
    AccountDao.kt         — CRUD справочника счетов
    CategoryDao.kt        — CRUD справочника категорий (listAll/byName/insert)
  model/
    Currency.kt           — enum валют (code/symbol/displayName)
    AccountEntity.kt      — таблица `accounts` (id, name, color) + палитра из 12 цветов
    CategoryEntity.kt     — таблица `categories` (id, name, isIncome) + палитра цветов для графиков
    TransactionEntity.kt  — таблица `transactions` (с accountId + categoryId + индексы)
    StatEntity.kt         — PeriodType (с ключами и shift) + таблица `stats` (см. §7)
  repository/
    TransactionRepository.kt — бизнес-логика + транзакции + CRUD счетов (см. §6)
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
    SettingsScreen.kt         — выбор языка
    StatsScreen.kt            — статистика за период: бар-чарты по категориям (см. §8)
  theme/Theme.kt              — Material3 dark/light палитра
  viewmodel/
    LockViewModel.kt          — старт-ап/вход/вайп
    FinanceViewModel.kt       — состояние дашборда + справочник категорий (см. §8)
    AccountsViewModel.kt      — CRUD счетов, переключение текущего
    SettingsViewModel.kt      — обёртка языка
    StatsViewModel.kt         — состояние экрана статистики (см. §8)
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
     `stats/{currency}` (валюта дашборда передаётся аргументом и читается
     StatsViewModel через SavedStateHandle) → `popBackStack()`.

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
  `hintSetup` («задайте ключ для шифрования») vs `hintEnter`
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
`timestamp`. Индексы `(accountId, currencyCode, id)` и `(categoryId)`.

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
имеет `version = 4`:
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

`DbHolder.build()` регистрирует обе миграции:
`addMigrations(MIGRATION_1_2, MIGRATION_2_3)` плюс
`fallbackToDestructiveMigration()` как страховку.

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
- `income/expense(acc, cur)` — читают строку `TOTAL` из `stats` (точечно по PK);
- `periodIncome/periodExpense(acc, cur, pt, key)` — сумма за конкретный период;
- `add(t)`/`remove(t)`/`wipe()` — обёрнуты в `db.db().withTransaction { }`
  (Room из `room-ktx`), внутри которых вставка/удаление транзакции И
  дельта-обновление `stats` (`applyDelta`) атомарны;
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
   (`ExpandMore`/`ExpandLess`) для сворачивания/разворачивания. В свернутом
   состоянии (по умолчанию, `balanceExpanded = false`) виден только заголовок;
   в развёрнутом — сумма баланса (белый символ валюты через `amountStr()`,
   число `IncomeGreen` при `≥ 0`, `error` при `< 0`), строка дохода/расхода.
2. Компактная статистика (`Row` из карточек `weight(1f)`) — чистая сумма за
   `DAY/WEEK/MONTH/YEAR`, мелкий шрифт, формат через `compact()`, цвет
   `IncomeGreen`/`error` по знаку. Показывается **только** когда
   `balanceExpanded = true` (скрывается вместе со сворачиванием баланса).
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
account» / RU «Сменить счёт»). Все операции
(баланс, история, статистика, добавление, удаление) идут только с
текущим `accountId` из `SettingsRepository`.

Диалоги (в конце тела Composable, поверх Scaffold):
- `viewed` — просмотр комментария записи по тапу на карточку.
- `toDelete` — подтверждение удаления (`AlertDialog` с категорией и суммой).
- `BackHandler` отключён, пока открыт любой диалог или FAB-окно.

**Карточки компактной статистики — кнопки**: каждая (`День/Неделя/Месяц/Год`)
окружена `Modifier.clickable(role = Role.Button, onClickLabel = s.statsTitle)`
и ведёт на экран `stats` (`onOpenStats`). Layout — вариант V1, двухстрочный
(в узкой колонке `weight(1f)` всё в один ряд не помещается и «съезжает»):
строка 1 — только подпись периода; строка 2 — иконка `BarChart`, затем
`compact(net)` (`weight(1f)` + ellipsis) и `ChevronRight` справа.
ripple/onClickLabel добавлены через clip+clickable поверх Card.

### 8.1 AddDlg (диалог добавления записи)
[`AddDlg`](app/src/main/java/com/example/financetracker/ui/screens/DashboardScreen.kt:388)
— `AlertDialog` с параметрами `currency`, `categories` (справочник из БД),
`onCreateCategory(name, isIncome): Int?`, `ok(amount, categoryId, note,
isIncome, timestamp)`. Внутри:
- **Сумма**: `OutlinedTextField` с `KeyboardOptions(keyboardType = Decimal)`
  и фильтром ввода (цифры + запятая/точка); парсинг `replace(',', '.')`,
  невалидный ввод подсвечивается `isError`; кнопка «Добавить» неактивна,
  пока сумма не положительна или не выбрана категория.
- **Тип** (Расход/Доход) — чипы `FilterChip`, **справа в той же строке** —
  `FilledTonalButton` с иконкой `CalendarMonth` и текущими датой/временем
  записи (`dd.MM HH:mm`, `SimpleDateFormat`). Тап открывает
  `DatePickerDialog` (Material3, UTC-конвертация `initialSelectedDateMillis`
  и обратно), после ОК сразу — диалог с `TimePicker` (`rememberTimePickerState`,
  24ч). Результат пикеров — `ts`, уходит в `ok` и далее в `timestamp`.
  Даты в будущем разрешены (планирование).
- **Категория**: `ExposedDropdownMenuBox` по отфильтрованному `catsFor`
  (справочник БД по `isIncome` выбранного типа, порядок id). В конце списка
  — пункт «+ Добавить новую» (`Icons.Default.Add` + `s.addNewCategory`):
  открывает вложенный `AlertDialog` с полем названия (по одному в строке)
  и кнопками ОК (`s.ok`) / Отмена (`s.cancel`); ОК создаёт категорию через
  `onCreateCategory`, выбирает её (`catId = id`) и добавляет в конец списка.
  Дубликат (NOCASE) выбирает существующую (см. репозиторий §6).
  При смене типа выбранная категория, не принадлежащая новому типу,
  откатывается на первую подходящую (`effectiveCat`).
- `note` — однострочное поле (как было).

### 8.2 StatsScreen (экран статистики)
[`StatsScreen`](app/src/main/java/com/example/financetracker/ui/screens/StatsScreen.kt:39)
+ [`StatsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/StatsViewModel.kt:45):
структура Column:
1. `TopAppBar` с кнопкой «Назад» (`Icons.AutoMirrored.Filled.ArrowBack` →
   `popBackStack()`) и заголовком `s.statsTitle`.
2. Строка `FilterChip`: День/Неделя/Месяц/Год (`vm.setType`).
3. Строка периода: `IconButton ChevronLeft` | заголовок периода
   (`s.periodTitle(type, key)` — см. §9) | `IconButton ChevronRight`.
   Кнопки листания (`vm.shift(±1)`) отключаются у границ истории
   (`minKey`/`maxKey` из `stats`, `canGoBack/canGoForward` в состоянии);
   стартовый ключ = текущий период, но не вне границ.
4. Два `BarChartCard` (Расходы сверху — красный, Доходы снизу — зелёный):
   строка заголовка + «Всего» за период (реальные итоги из агрегирующей
   строки `stats`, не искажённые топ-N), ниже — горизонтальные лежачие
   столбики (полоски с весом `value/max`, скругление, фон track
   `colorScheme.surface`), не больше `TOP_N = 7` категорий с наибольшим
   значением, отсортированы по убыванию; подпись = категория (`s.cat(name)`),
   цвет = `CategoryEntity.colorFor(id)`. Пустой период → `s.noStatsData`.
   Рисование чистым Compose (`Box`+`weight(frac)`), без сторонних библиотек
   (офлайн-политика).

### 8.3 Прочие ViewModel
[`StatsViewModel`](app/src/main/java/com/example/financetracker/ui/viewmodel/StatsViewModel.kt:45)
— `state: StateFlow<StatsState>` (type, key, minKey, maxKey, currency,
expenseBars, incomeBars, totalExpense, totalIncome, loading, empty).
Валюта активна та же, что на дашборде — приходит навигационным аргументом
`stats/{currency}` из `SavedStateHandle`. Все обращения к БД в `try/catch`
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

## 12. Известные нюансы

- **Экран статистики и пустой ключ периода**: `StatsState` при создании имеет
  `key = ""` (до первой загрузки из БД). `periodTitle()`, `canGoBack`/
  `canGoForward` и `shift()` обязаны корректно обрабатывать пустой ключ —
  `PeriodType.shift("")`/парсинг ключа бросают исключение и роняют приложение
  на первой же композиции (краш по клику на карточку дашборда). Поэтому:
  пустой key → false для кнопок/пустой заголовок, стартовое состояние —
  `loading = true`, `shift()` с пустым ключом — no-op.

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

## 13. Актуальность документации

**Этот файл необходимо держать в актуальном состоянии при заметных изменениях
проекта.** Любая существенная правка — новая сущность/миграция, изменение
архитектуры слоёв (DAO/Repository/ViewModel/Screen), новый экран или маршрут,
смена схемы БД, изменение правил безопасности или производительности, добавление
типовых операций — обязана сопровождаться обновлением соответствующего раздела
[`DEV.md`](DEV.md). Перед push (см. чек-лист §11) убедитесь, что описание в
DEV.md отражает фактическое состояние кода: устаревшая документация хуже, чем
её отсутствие.