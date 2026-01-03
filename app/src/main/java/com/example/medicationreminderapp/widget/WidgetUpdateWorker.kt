package com.example.medicationreminderapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medicationreminderapp.AppRepository
import com.example.medicationreminderapp.Medication
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sharedPreferences = applicationContext.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val medicationJson = sharedPreferences.getString(AppRepository.KEY_MEDICATION_DATA, null)

        val medications: List<Medication> = if (medicationJson != null) {
            gson.fromJson(medicationJson, object : TypeToken<List<Medication>>() {}.type)
        } else {
            emptyList()
        }

        val nextMedication = findNextMedication(medications)

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, MedicationWidgetProvider::class.java))

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(applicationContext, appWidgetManager, appWidgetId, nextMedication)
        }

        return Result.success()
    }

    private fun findNextMedication(medications: List<Medication>): Pair<Medication, Long>? {
        var nextMedicationTime: Long = Long.MAX_VALUE
        var nextMedication: Medication? = null

        val now = Calendar.getInstance()

        for (medication in medications) {
            for (timeInMillis in medication.times.values) {
                val alarmTime = Calendar.getInstance().apply {
                    this.timeInMillis = timeInMillis
                }

                if (alarmTime.before(now)) {
                    alarmTime.add(Calendar.DAY_OF_YEAR, 1)
                }

                if (alarmTime.timeInMillis < nextMedicationTime) {
                    nextMedicationTime = alarmTime.timeInMillis
                    nextMedication = medication
                }
            }
        }

        return if (nextMedication != null) {
            Pair(nextMedication, nextMedicationTime)
        } else {
            null
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        nextMedicationInfo: Pair<Medication, Long>?
    ) {
        val views = RemoteViews(context.packageName, R.layout.medication_widget)

        if (nextMedicationInfo != null) {
            val (medication, time) = nextMedicationInfo
            val timeFormat = SimpleDateFormat("HH:mm a", Locale.getDefault())

            views.setTextViewText(R.id.widget_medication_name, medication.name)
            views.setTextViewText(R.id.widget_medication_time, timeFormat.format(time))
        } else {
            views.setTextViewText(R.id.widget_medication_name, "沒有即將到來的藥物")
            views.setTextViewText(R.id.widget_medication_time, "")
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}