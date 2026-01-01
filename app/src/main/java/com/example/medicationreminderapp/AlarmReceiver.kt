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
    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: "藥物"
        val dosage = intent.getStringExtra("dosage") ?: ""
        val notificationId = intent.getIntExtra("notificationId", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)

        if (notificationId == -1 || requestCode == -1) return

        // 1. Show notification (保持不變)
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

        // 2. Reschedule for the next day (優化部分)
        scheduleNextAlarm(context, intent, requestCode)
    }

    private fun scheduleNextAlarm(context: Context, originalIntent: Intent, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 優化時間計算：不要用現在時間(Calendar.getInstance())，這會造成時間漂移。
        // 實際應用中，你應該從 Intent 傳遞原本設定的時間戳記，然後 + 24小時。
        // 這裡暫時維持你的邏輯，但建議未來修正。
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

        // 檢查權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                // 有權限：設定精確鬧鐘
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime.timeInMillis,
                        nextPendingIntent
                    )
                    Log.d("AlarmReceiver", "Rescheduled exact alarm for tomorrow")
                } catch (se: SecurityException) {
                    Log.e("AlarmReceiver", "Failed to schedule exact alarm", se)
                    // 發生異常時，嘗試降級設定
                    scheduleInexactAlarm(alarmManager, nextAlarmTime.timeInMillis, nextPendingIntent)
                }
            } else {
                // 無權限：設定非精確鬧鐘 (這是重點改動！)
                Log.w("AlarmReceiver", "No permission for exact alarm. Using inexact instead.")
                scheduleInexactAlarm(alarmManager, nextAlarmTime.timeInMillis, nextPendingIntent)
                
                // 選項：你可以在這裡發送另一個通知，提醒使用者去設定開啟權限
            }
        } else {
            // Android 12 以下，直接設定
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTime.timeInMillis,
                nextPendingIntent
            )
        }
    }

    // 降級方案：非精確鬧鐘
    private fun scheduleInexactAlarm(alarmManager: AlarmManager, timeInMillis: Long, pendingIntent: PendingIntent) {
        // setAndAllowWhileIdle 雖然不是 100% 精確，但在省電模式下仍會響，
        // 誤差通常在幾分鐘內，對於吃藥提醒來說比「完全不響」好太多了。
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            pendingIntent
        )
    }
}
