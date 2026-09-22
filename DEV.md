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

Релизный подписанный APK требует секретов GitHub Actions (см.
[`.github/workflows/build.yml`](.github/workflows/build.yml)):
`KEYSTORE_BASE64` (раскодируется в `release.keystore`, путь передаётся как env
`KEYSTORE_PATH`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
(см. [`app/build.gradle.kts`](app/build.gradle.kts:20)). Без кейстора CI
собирает debug-APK как fallback.

---

## 3. Карта исходников

Корень пакетов: `app/src/main/java/com/example/financetracker/`

```
FinanceTrackerApp.kt      — @HiltAndroidApp; грузит нативную либу sqlcipher
MainActivity.kt           — @AndroidEntryPoint; единственная Activity, Compose host

data/
  local/
    AppDatabase.kt        — @Database v2, DAO-и, MIGRATION_1_2
    DbHolder.kt           — ленивое открытие зашифрованной БД (см. §5)
    TransactionDao.kt     — CRUD + keyset-пагинация
    StatDao.kt            — инкрементальные суммы по периодам
  model/
    Currency.kt           — enum валют (code/symbol/displayName)
    TransactionEntity.kt  — таблица `transactions`
    StatEntity.kt         — PeriodType + таблица `stats` (см. §7)
  repository/
    TransactionRepository.kt — бизнес-логика + транзакции (см. §6)
  security/
    PatternLockManager.kt — узор, соль, PBKDF2, счётчик попыток, вайп
  settings/
    SettingsRepository.kt — язык (EN/RU) в SharedPreferences

di/
  AppModule.kt            — пустой Hilt-модуль (БД ленивая, см. §5)

ui/
  components/PatternLock.kt   — Canvas-виджет 3×3 узора
  locale/AppLocale.kt         — Strings, StringsEn/Ru, LocalStrings, cat()
  navigation/AppNavGraph.kt   — NavHost: lock → main → settings
  screens/
    LockScreen.kt             — экран узора
    DashboardScreen.kt        — главный экран (см. §8)
    SettingsScreen.kt         — выбор языка
  theme/Theme.kt              — Material3 dark/light палитра
  viewmodel/
    LockViewModel.kt          — старт-ап/вход/вайп
    FinanceViewModel.kt       — состояние дашборда (см. §8)
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
   - `main` (Dashboard) → кнопка настроек → `settings` → `popBackStack()`.

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
`transactions` ([`TransactionEntity`](app/src/main/java/com/example/financetracker/data/model/TransactionEntity.kt)):
`id` (PK, autoGenerate), `amount`, `currencyCode`, `category`, `note`,
`isIncome`, `timestamp`.

`stats` ([`StatEntity`](app/src/main/java/com/example/financetracker/data/model/StatEntity.kt)):
PK = составной `(periodType, periodKey, currencyCode)`, поля `income`, `expense`.

### Миграции
[`AppDatabase`](app/src/main/java/com/example/financetracker/data/local/AppDatabase.kt)
имеет `version = 2` и `MIGRATION_1_2`: создаёт `stats` и заполняет её
агрегацией из `transactions` по всем периодам. `DbHolder.build()` регистрирует
миграцию через `addMigrations(AppDatabase.MIGRATION_1_2)` плюс
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
— единственная точка работы с БД из UI-слоя:
- `page(cur, lastId, limit)` — keyset-страница только активной валюты;
- `income/expense(cur)` — читают строку `TOTAL` из `stats` (точечно по PK);
- `periodIncome/periodExpense(cur, pt, key)` — сумма за конкретный период;
- `add(t)`/`remove(t)`/`wipe()` — обёрнуты в `db.db().withTransaction { }`
  (Room из `room-ktx`), внутри которых вставка/удаление транзакции И
  дельта-обновление `stats` (`applyDelta`) атомарны;
- `applyDelta(t, sign)` — для каждого `PeriodType` делает `insertIfAbsent`
  (создаёт нулевую строку периода при необходимости) и `addDelta`
  (`UPDATE ... SET income = income + :inc ...`). `sign = +1` при добавлении,
  `-1` при удалении. Так статистика всегда согласована с транзакциями.

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
- `reload()` — первая страница (`page(cur,0,20)`) + `refreshTotals()`.
- `loadMore()` — следующая страница от `items.last().id`, с защитой от гонок
  (`loadingMore`/`hasMore`/пустой список).
- `refreshTotals()` → `refreshPeriods()` — перечитывает TOTAL и 4 периода.
- `add(...)` — пишет в БД, затем **вставляет запись в начало окна без
  перечитывания** (используя возвращённый `id`).
- `remove(t)` — удаляет, фильтрует из окна, при опустошении окна перечитывает/
  дозагружает.
- Все обращения к БД в `try/catch`; ошибка → безопасное пустое состояние
  (БД не роняет процесс).

### DashboardScreen
[`ui/screens/DashboardScreen.kt`](app/src/main/java/com/example/financetracker/ui/screens/DashboardScreen.kt)
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

Диалоги (в конце тела Composable, поверх Scaffold):
- `viewed` — просмотр комментария записи по тапу на карточку: заголовок
  «категория: сумма», дата + текст заметки в `verticalScroll` с
  `heightIn(max=320.dp)` (прокрутка длинного текста), кнопка «Закрыть».
- `toDelete` — подтверждение удаления (`AlertDialog` с категорией и суммой),
  удаление только по кнопке «Удалить».
- `BackHandler` отключён, пока открыт любой диалог или FAB-окно.

Хелперы в файле: `fmt()` (полный формат с валютой, для диалогов),
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