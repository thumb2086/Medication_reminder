package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MedicationTakenEntryPoint {
    fun getAppRepository(): AppRepository
}

class MedicationTakenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)

        if (notificationId == -1 || requestCode == -1) return

        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedicationTakenEntryPoint::class.java
        )
        val repository = hiltEntryPoint.getAppRepository()

        // 1. Mark medication as taken in SharedPreferences
        val prefs = context.getSharedPreferences("medication_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("medication_taken_$notificationId", true)
        }

        // 2. Process local data update
        repository.processMedicationTaken(notificationId)

        // 3. Cancel the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 4. Cancel the MissedDoseCheckReceiver alarm
        val missedDoseIntent = Intent(context, MissedDoseCheckReceiver::class.java)
        val missedDosePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + AlarmReceiver.MISSED_DOSE_CHECK_REQUEST_CODE_OFFSET,
            missedDoseIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (missedDosePendingIntent != null) {
            alarmManager.cancel(missedDosePendingIntent)
        }

        // 5. Cancel the next day's alarm (existing logic)
        val alarmIntent = Intent(context, AlarmReceiver::class.java)
        val alarmPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode, 
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent)
        }
    }
}