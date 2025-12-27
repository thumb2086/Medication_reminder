package com.example.medicationreminderapp

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration

object ContextUtils {

    fun updateLocale(context: Context): ContextWrapper {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Read the setting, defaulting to 1.0f
        val fontScale = sharedPreferences.getFloat("font_scale", 1.0f)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        // Force-set the fontScale
        configuration.fontScale = fontScale

        // Create a context with the new configuration
        val newContext = context.createConfigurationContext(configuration)
        
        return ContextWrapper(newContext)
    }
}
