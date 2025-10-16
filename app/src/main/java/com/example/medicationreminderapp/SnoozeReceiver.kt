package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", 0)
        val medicationName = intent.getStringExtra("medicationName")
        val dosage = intent.getStringExtra("dosage")
        val originalAlarmTime = intent.getLongExtra("originalAlarmTimeOfDay", 0)

        // Dismiss the original notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Schedule a new alarm for 10 minutes later
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("notificationId", notificationId)
            putExtra("medicationName", medicationName)
            putExtra("dosage", dosage)
            putExtra("originalAlarmTimeOfDay", originalAlarmTime) // Keep passing the original time
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, 
            notificationId, // Use the same ID to update the original pending intent
            snoozeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 10)
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime.timeInMillis, snoozePendingIntent)
    }
}