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
    AppDatabase.kt        — @Database v3, DAO-и, MIGRATION_1_2 + MIGRATION_2_3
    DbHolder.kt           — ленивое открытие зашифрованной БД (см. §5)
    TransactionDao.kt     — CRUD + keyset-пагинация (по счёту + валюте)
    StatDao.kt            — инкрементальные суммы (по счёту + периоду + валюте)
    AccountDao.kt         — CRUD справочника счетов
  model/
    Currency.kt           — enum валют (code/symbol/displayName)
    AccountEntity.kt      — таблица `accounts` (id, name, color) + палитра из 12 цветов
    TransactionEntity.kt  — таблица `transactions` (с accountId + индекс)
    StatEntity.kt         — PeriodType + таблица `stats` (PK включает accountId) (см. §7)
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
  locale/AppLocale.kt         — Strings, StringsEn/Ru, LocalStrings, cat()
  navigation/AppNavGraph.kt   — NavHost: lock → main → settings / accounts
  screens/
    LockScreen.kt             — экран узора
    DashboardScreen.kt        — главный экран (см. §8)
    AccountsScreen.kt         — CRUD-справочник счетов
    SettingsScreen.kt         — выбор языка
  theme/Theme.kt              — Material3 dark/light палитра
  viewmodel/
    LockViewModel.kt          — старт-ап/вход/вайп
    FinanceViewModel.kt       — состояние дашборда (см. §8)
    AccountsViewModel.kt      — CRUD счетов, переключение текущего
    SettingsViewModel.kt      — обёртка языка
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
     → `popBackStack()` (возврат без смены) или `onSwitchAccount` + `popBackStack()`.

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
`category`, `note`, `isIncome`, `timestamp`. Индекс `(accountId, currencyCode, id)`.

`stats` ([`StatEntity`](app/src/main/java/com/example/financetracker/data/model/StatEntity.kt)):
PK = составной `(accountId, periodType, periodKey, currencyCode)`,
поля `income`, `expense`.

### Миграции
[`AppDatabase`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt)
имеет `version = 3`:
- `MIGRATION_1_2` — создаёт `stats` (без `accountId`) и заполняет её
  агрегацией из `transactions` по всем периодам.
- `MIGRATION_2_3` — вводит многоучётность:
  1. Создаёт таблицу `accounts`, вставляет дефолтный счёт `id=1`
     (`"Мои финансы"`, первый цвет палитры).
  2. `ALTER TABLE transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1`
     + создание индекса `(accountId, currencyCode, id)`.
  3. Пересоздаёт `stats` с `accountId` в составном PK,
     переносит данные (все со счётом 1) из `transactions`.

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

---

## 7. Статистика и периоды

[`PeriodType`](app/src/main/java/com/example/financetracker/data/model/StatEntity.kt:15):
`TOTAL` (ключ `""`), `DAY` (`yyyy-MM-dd`), `WEEK` (`yyyy-Www`, ISO), `MONTH`
(`yyyy-MM`), `YEAR` (`yyyy`). Каждый период вычисляет ключ из `timestamp`
в системной таймзоне (`dateOf()`).

Вклад транзакции идёт ровно в один ключ каждого периода её валюты. Итог по
валюте за всё время = строка `TOTAL`. Суммы за день/неделю/месяц/год на
дашборде = точечные запросы по текущим ключам. Никаких `SUM()` по всей
таблице `transactions`.

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
- `add(...)` — пишет в БД (с `accountId` из `SettingsRepository`),
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
   подстановкой `{CURRENCY}` (см. §10).
4. FAB → `AddDlg` (доход/расход, сумма, категория, заметка).

**TopAppBar**: вместо статического `s.appTitle` — название текущего счёта
(`acc?.name ?: s.appTitle`) с **тонкой обводкой** (`border(1.dp,
Color(acc?.color)`) и тапом → `onOpenAccounts()`. Все операции
(баланс, история, статистика, добавление, удаление) идут только с
текущим `accountId` из `SettingsRepository`.

Диалоги (в конце тела Composable, поверх Scaffold):
- `viewed` — просмотр комментария записи по тапу на карточку.
- `toDelete` — подтверждение удаления (`AlertDialog` с категорией и суммой).
- `BackHandler` отключён, пока открыт любой диалог или FAB-окно.

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
`stringsFor(lang)`; `Strings.cat(name)` — перевод категории из `categories`.

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

**Новая категория:** добавить ключ+перевод в `catsEn` и `catsRu` и в списки
`cats` в `AddDlg`.

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