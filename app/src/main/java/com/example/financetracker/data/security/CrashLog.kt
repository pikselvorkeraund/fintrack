package com.example.financetracker.data.security

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Персистентный отладочный слой для диагностики крашей release-сборки.
 *
 * 1) [mark] — короткие контрольные точки («процесс дожил до X»), пишутся
 *    ДО потенциально фатальной операции. Переживают даже нативный SIGSEGV
 *    внутри SQLCipher/SQLite, который невозможно перехватить через
 *    Thread.setDefaultUncaughtExceptionHandler.
 * 2) [save] — полный Java-стектрейс через глобальный UncaughtExceptionHandler.
 *    Ловит все падения, кроме нативных.
 *
 * При следующем запуске [readAndClear] возвращает накопленную картину и
 * очищает файлы — экран блокировки показывает её вместо молчаливого креша.
 */
object CrashLog {
    private const val CHECKPOINT_FILE = "db_checkpoint.txt"
    private const val CRASH_FILE = "crash.txt"

    /** Записать контрольную точку (например "open-start", "open-ok"). */
    fun mark(ctx: Context, tag: String) {
        runCatching {
            File(ctx.filesDir, CHECKPOINT_FILE).writeText(
                "${System.currentTimeMillis()} $tag"
            )
        }
    }

    /** Записать Java-стектрейс необработанного падения. */
    fun save(ctx: Context, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        runCatching { File(ctx.filesDir, CRASH_FILE).writeText(sw.toString()) }
    }

    /**
     * Возвращает текст для показа на экране блокировки и очищает файлы.
     * null — если после прошлой чистки падений/точек нет.
     */
    fun readAndClear(ctx: Context): String? {
        val cf = File(ctx.filesDir, CRASH_FILE)
        val pf = File(ctx.filesDir, CHECKPOINT_FILE)
        val crash = runCatching { if (cf.exists()) cf.readText() else null }.getOrNull()
        val point = runCatching { if (pf.exists()) pf.readText() else null }.getOrNull()
        runCatching { cf.delete() }
        runCatching { pf.delete() }
        if (crash == null && point == null) return null
        // Для экрана блокировки: сначала Java-стектрейс (если есть), иначе
        // последняя контрольная точка + её отсутствие = признак нативного креша.
        val head = if (crash != null) crash else "no java crash — likely native SIGSEGV"
        val tail = if (point != null) "\nlast checkpoint: $point" else ""
        return (head + tail).take(600)
    }
}