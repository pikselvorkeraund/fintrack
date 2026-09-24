package com.example.financetracker

import android.app.Application
import com.example.financetracker.data.security.CrashLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FinanceTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Перехват необработанных Java-крашей: стектрейс сохраняется в файл
        // и показывается на экране блокировки при следующем запуске.
        // (Нативный SIGSEGV до этого слоя не доходит — для него работают
        // контрольные точки CrashLog.mark в DbHolder.)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            CrashLog.save(this, ex)
            prev?.uncaughtException(thread, ex)
        }
        // SQLCipher требует явной загрузки нативной библиотеки
        // до первого обращения к базе данных
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            // Если нативная либа не загрузилась (release/R8/shrinker/ABI) —
            // фиксируем это и НЕ роняем процесс: БД не откроется, но экран
            // блокировки покажет диагностику вместо мгновенного вылета.
            CrashLog.save(this, e)
        }
    }
}
