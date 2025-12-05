package com.example.medicationreminderapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: "藥物"
        val dosage = intent.getStringExtra("dosage") ?: ""
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val takenIntent = Intent(context, MedicationTakenReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }

        // Request code for PendingIntent should be unique. Using notificationId is a good practice.
        val takenPendingIntent = PendingIntent.getBroadcast(context, notificationId, takenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("該吃藥了！")
            .setContentText("請服用 $medicationName, 劑量 $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher_foreground, "我已服用", takenPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
