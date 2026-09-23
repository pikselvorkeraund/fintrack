# =====================================================================
#  FinTrack proguard / R8 rules (release minify).
#
#  Для небольшого приложения минификация собственного кода приносит
#  больше проблем (Room/Hilt/Compose используют генерированные классы),
#  чем пользы. Стратегия:
#    - весь наш код (com.example.financetracker) — НЕ трогаем;
#    - SQLCipher + SQLite framework — keep (нативные вызовы);
#    - Room / Hilt / Dagger — keep сгенерированных имплементаций;
#    - остальное (Compose, корутины, androidx) — R8 минифицирует.
# =====================================================================

# --- Наш код: целиком (компилируется в одном app-модуле) ---
-keep class com.example.financetracker.** { *; }

# --- SQLCipher (нативная либа, JNI, open-helper factory) ---
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# --- SQLite framework (Room → SupportOpenHelperFactory → SQLite) ---
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# --- Room (сгенерированные RoomDatabase_Impl, DAO_Impl) ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-dontwarn androidx.room.**

# --- Hilt / Dagger (сгенерированные компоненты, инжекты) ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
-keep class *HiltComponents* { *; }
-keep class *HiltModules* { *; }
-keep class *Hilt_* { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-dontwarn dagger.**
-dontwarn javax.inject.**

# --- Коррутины / Flow (не ломать StateFlow) ---
-dontwarn kotlinx.**

# --- Общие атрибуты ---
-keepattributes *Annotation*
-keepattributes Signature