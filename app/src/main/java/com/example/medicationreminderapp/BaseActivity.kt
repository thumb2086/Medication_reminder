package com.example.medicationreminderapp

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    // 1. At Context connection time, directly replace the Context
    override fun attachBaseContext(newBase: Context) {
        // Use ContextUtils to generate a new Context
        val newContext = ContextUtils.updateLocale(newBase)
        super.attachBaseContext(newContext)
    }

    // 2. To prevent some phones (like Xiaomi/Samsung) from overriding Resources at Runtime,
    // we add a second layer of interception here (this is the savior for many "invalid" cases)
    override fun getResources(): Resources {
        val res = super.getResources()
        val config = res.configuration
        
        // Read the current setting value
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val targetScale = sharedPreferences.getFloat("font_scale", 1.0f)
        
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
