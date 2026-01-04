package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val MISSED_DOSE_CHECK_REQUEST_CODE_OFFSET = 1000000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: "藥物"
        val dosage = intent.getStringExtra("dosage") ?: ""
        val notificationId = intent.getIntExtra("notificationId", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)

        if (notificationId == -1 || requestCode == -1) return

        // 1. Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val takenIntent = Intent(context, MedicationTakenReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("requestCode", requestCode)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("該吃藥了！")
            .setContentText("請服用 $medicationName, 劑量 $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, "我已服用", takenPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)

        // 2. Schedule a check for missed dose
        scheduleMissedDoseCheck(context, notificationId, medicationName)

        // 3. Reschedule for the next day
        scheduleNextAlarm(context, intent, requestCode)
    }

    private fun scheduleMissedDoseCheck(context: Context, notificationId: Int, medicationName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val missedDoseIntent = Intent(context, MissedDoseCheckReceiver::class.java).apply {
            putExtra("notificationId", notificationId)
            putExtra("medicationName", medicationName)
        }

        val missedDosePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + MISSED_DOSE_CHECK_REQUEST_CODE_OFFSET,
            missedDoseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30) // Check after 30 minutes
        }

        // Mark as pending
        val prefs = context.getSharedPreferences("medication_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("medication_taken_$notificationId", false).apply()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            checkTime.timeInMillis,
            missedDosePendingIntent
        )
    }

    private fun scheduleNextAlarm(context: Context, originalIntent: Intent, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nextAlarmTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtras(originalIntent.extras!!)
        }
        
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime.timeInMillis,
                        nextPendingIntent
                    )
                } catch (se: SecurityException) {
                    scheduleInexactAlarm(alarmManager, nextAlarmTime.timeInMillis, nextPendingIntent)
                }
            } else {
                scheduleInexactAlarm(alarmManager, nextAlarmTime.timeInMillis, nextPendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTime.timeInMillis,
                nextPendingIntent
            )
        }
    }

    private fun scheduleInexactAlarm(alarmManager: AlarmManager, timeInMillis: Long, pendingIntent: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            pendingIntent
        )
    }
}
