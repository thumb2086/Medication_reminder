package com.example.medicationreminderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPreferences = context.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val alarmScheduler = AlarmScheduler(context)

            sharedPreferences.getString(AppRepository.KEY_MEDICATION_DATA, null)?.let {
                try {
                    val medications: List<Medication> = gson.fromJson(it, object : TypeToken<List<Medication>>() {}.type)
                    medications.forEach { medication ->
                        alarmScheduler.schedule(medication)
                    }
                } catch (_: Exception) {
                    // Log error or handle gracefully
                }
            }
        }
    }
}
