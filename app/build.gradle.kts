plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.example.financetracker"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.financetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1-diag"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        vectorDrawables { useSupportLibrary = true }
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && File(ks).exists()) {
                storeFile = File(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
        create("debugFixed") {
            val ks = System.getenv("DEBUG_KEYSTORE_PATH")
            if (ks != null && File(ks).exists()) {
                storeFile = File(ks)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        release {
            // ДИАГНОСТИКА: R8/shrinker временно ОТКЛЮЧЕНЫ (версия 4).
            // Все Java-throwables (включая Error) уже перехвачены, keep
            // в proguard-rules.pro полный — но release продолжает падать
            // молча сразу после ввода узора. Единственные оставшиеся
            // кандидаты: (а) нативный SIGSEGV в SQLCipher, вызванный
            // R8/resource-shrinker, или (б) ошибка за пределами всех
            // цепочек (миграция/навигация). Отключение minify точно
            // разделяет эти два случая: заработает = R8 (а);
            // продолжит падать = миграция/код (б) — и CrashLog на экране
            // блокировки при повторном запуске покажет последнюю контрольную
            // точку unlock. Вернуть true после диагностики.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null && File(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            val dks = System.getenv("DEBUG_KEYSTORE_PATH")
            if (dks != null && File(dks).exists()) {
                signingConfig = signingConfigs.getByName("debugFixed")
            }
        }
    }
    bundle {
        language { enableSplit = false }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.nav.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav)
    implementation(libs.security.crypto)
    implementation(libs.coroutines)
    debugImplementation(libs.compose.tooling)
}
