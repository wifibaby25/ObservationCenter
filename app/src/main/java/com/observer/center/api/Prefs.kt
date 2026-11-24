package com.observer.center.api

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("obs", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = sp.getString("base_url", "") ?: ""
        set(v) = sp.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    fun clearSession() = sp.edit().remove("cookies").apply()

    var cookieStore: String
        get() = sp.getString("cookies", "") ?: ""
        set(v) = sp.edit().putString("cookies", v).apply()

    var lastCrash: String
        get() = sp.getString("last_crash", "") ?: ""
        set(v) = sp.edit().putString("last_crash", v).apply()
}
