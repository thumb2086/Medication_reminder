package com.example.medicationreminderapp.util

import android.content.Context
import android.content.res.Configuration

object FontUtil {

    fun getUpdatedConfiguration(context: Context): Configuration {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val fontSize = prefs.getString("font_size", "medium")
        val scale = when (fontSize) {
            "small" -> 0.85f
            "medium" -> 1.0f
            "large" -> 1.15f
            else -> 1.0f
        }

        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = scale
        return configuration
    }
}
