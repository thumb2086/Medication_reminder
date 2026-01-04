package com.example.medicationreminderapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class MissedDoseCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: "Medication"
        val notificationId = intent.getIntExtra("notificationId", -1)

        if (notificationId == -1) return

        val prefs = context.getSharedPreferences("medication_prefs", Context.MODE_PRIVATE)
        val isTaken = prefs.getBoolean("medication_taken_$notificationId", false)

        if (!isTaken) {
            // Medication not taken, send SMS
            val forwardingNumber = prefs.getString("forwarding_contact_number", null)
            if (forwardingNumber != null) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val smsManager = context.getSystemService(SmsManager::class.java)
                        val message = "[Medication Reminder] The user may have missed their dose of $medicationName."
                        smsManager.sendTextMessage(forwardingNumber, null, message, null, null)
                    } catch (_: Exception) {
                        // Handle exception
                    }
                } else {
                    // Handle permission not granted
                }
            }
        }

        // Clean up the preference
        prefs.edit {
            remove("medication_taken_$notificationId")
        }
    }
}