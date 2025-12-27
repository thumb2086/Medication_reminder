package com.example.medicationreminderapp

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    // 1. At Context connection time, directly replace the Context
    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = newBase.getSharedPreferences("app_prefs", MODE_PRIVATE)
        val fontScale = sharedPreferences.getFloat("font_scale", 1.0f)

        val configuration = newBase.resources.configuration
        configuration.fontScale = fontScale
        val updatedContext = newBase.createConfigurationContext(configuration)

        super.attachBaseContext(ContextWrapper(updatedContext))
    }

    // 2. To prevent some phones (like Xiaomi/Samsung) from overriding Resources at Runtime,
    // we add a second layer of interception here (this is the savior for many "invalid" cases)
    override fun getResources(): Resources {
        val res = super.getResources()
        val config = res.configuration
        
        // Read the current setting value
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val targetScale = sharedPreferences.getFloat("font_scale", 1.15f)
        
        // If the current resource's configuration doesn't match the target, force the correction
        if (config.fontScale != targetScale) {
            val newConfig = Configuration(config)
            newConfig.fontScale = targetScale
            // Although updateConfiguration is deprecated,
            // it's the only way to make it take effect immediately at the getResources level.
            @Suppress("DEPRECATION")
            res.updateConfiguration(newConfig, res.displayMetrics)
        }
        return res
    }
}
