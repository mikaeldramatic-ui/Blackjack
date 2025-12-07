package com.example.blackjack

import android.content.Context

object AppContext {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun resId(name: String): Int {
        return appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
    }
}