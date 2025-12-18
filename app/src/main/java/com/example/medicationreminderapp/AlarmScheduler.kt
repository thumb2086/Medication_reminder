package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(medication: Medication) {
        medication.times.values.forEachIndexed { index, timeInMillis ->
            val alarmTime = Calendar.getInstance().apply {
                this.timeInMillis = timeInMillis
            }

            // If the alarm time is in the past, schedule it for the next day.
            if (alarmTime.before(Calendar.getInstance())) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("medicationName", medication.name)
                putExtra("dosage", medication.dosage)
                putExtra("notificationId", medication.slotNumber) // Pass slotNumber as notificationId
            }

            // Make request code unique for each alarm time
            val requestCode = medication.id * 100 + index 

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                alarmTime.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    fun cancel(medication: Medication) {
        medication.times.keys.forEachIndexed { index, _ ->
            val requestCode = medication.id * 100 + index
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}
