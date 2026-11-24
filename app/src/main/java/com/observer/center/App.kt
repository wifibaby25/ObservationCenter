package com.observer.center

import android.app.Application
import com.observer.center.api.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val text = sw.toString()
                Prefs.lastCrash = text.take(600)
            } catch (_: Throwable) {}
            default?.uncaughtException(t, e)
        }
    }
}
