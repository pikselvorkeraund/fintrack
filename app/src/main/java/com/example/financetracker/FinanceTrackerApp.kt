package com.example.financetracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FinanceTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SQLCipher требует явной загрузки нативной библиотеки
        // до первого обращения к базе данных
        System.loadLibrary("sqlcipher")
    }
}
