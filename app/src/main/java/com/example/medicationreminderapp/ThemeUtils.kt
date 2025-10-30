package com.example.medicationreminderapp

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {

    fun setTheme(activity: Activity, theme: String?) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getTheme(context: Context): String {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> "light"
            AppCompatDelegate.MODE_NIGHT_YES -> "dark"
            else -> "system"
        }
    }
}
