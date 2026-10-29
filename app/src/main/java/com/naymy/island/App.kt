package com.naymy.island

import android.app.Application
import android.util.Log
import java.io.File

// Ловит любое падение и сохраняет текст ошибки, чтобы приложение показало его при следующем запуске.
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(e)) } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
    }
}
