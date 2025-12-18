package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MedicationTakenEntryPoint {
    fun getAppRepository(): AppRepository // Access a singleton repository instead
}

class MedicationTakenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (notificationId == -1) return

        // Use Hilt EntryPoint to access the correct singleton repository
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MedicationTakenEntryPoint::class.java
        )
        val repository = hiltEntryPoint.getAppRepository()

        // 1. Process local data update (for chart/log) via repository
        repository.processMedicationTaken(notificationId)

        // 2. Cancel notification (fixes the "not disappearing" issue)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // 3. Cancel any pending snooze alarms for this notification
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, AlarmReceiver::class.java) // Snooze schedules another alarm
        val alarmPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId, // Request code is the notification ID
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent)
        }
    }
}