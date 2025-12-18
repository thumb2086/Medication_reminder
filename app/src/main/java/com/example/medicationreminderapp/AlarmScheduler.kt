package com.example.medicationreminderapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(medication: Medication) {
        medication.times.values.forEachIndexed { index, timeInMillis ->
            val alarmTime = Calendar.getInstance().apply {
                this.timeInMillis = timeInMillis
            }

            if (alarmTime.before(Calendar.getInstance())) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("medicationName", medication.name)
                putExtra("dosage", medication.dosage)
                putExtra("notificationId", medication.slotNumber)
                putExtra("requestCode", medication.id * 100 + index)
            }

            val requestCode = medication.id * 100 + index

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Cannot schedule exact alarms. App lacks permission.")
                // Optionally, guide user to grant permission
                return@forEachIndexed
            }

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    pendingIntent
                )
            } catch (se: SecurityException) {
                Log.e("AlarmScheduler", "SecurityException while scheduling alarm.", se)
                // Handle exception, maybe notify user that alarms can't be set
            }
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
