package com.example.medicationreminderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.medicationreminderapp.data.database.MedicationDao
import com.example.medicationreminderapp.data.database.MedicationEntity
import com.example.medicationreminderapp.data.database.TakenRecordDao
import com.example.medicationreminderapp.data.database.TakenRecordEntity
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class MedicationTakenRecord(val timestamp: Long)

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val takenRecordDao: TakenRecordDao
) {

    // Data StateFlows
    private val _medicationList = MutableStateFlow<List<Medication>>(emptyList())
    val medicationList: StateFlow<List<Medication>> = _medicationList.asStateFlow()

    private val _dailyStatusMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyStatusMap: StateFlow<Map<String, Int>> = _dailyStatusMap.asStateFlow()

    private val _takenRecords = MutableStateFlow<List<MedicationTakenRecord>>(emptyList())

    private val _complianceRate = MutableStateFlow(0f)
    val complianceRate: StateFlow<Float> = _complianceRate.asStateFlow()

    // Sensor Data
    private val _historicSensorData = MutableStateFlow<List<SensorDataPoint>>(emptyList())
    val historicSensorData: StateFlow<List<SensorDataPoint>> = _historicSensorData.asStateFlow()

    private val _isEngineeringMode = MutableStateFlow(false)
    val isEngineeringMode: StateFlow<Boolean> = _isEngineeringMode.asStateFlow()

    private val historicDataBuffer = mutableListOf<SensorDataPoint>()

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO) // Repository scope for IO operations

    init {
        scope.launch {
            checkAndMigrateData()
        }

        // Now, the repository's data streams are powered by Room's Flows.
        medicationDao.getAllMedications().onEach { entities ->
            _medicationList.value = entities.map { it.toDomainModel() }
            updateDailyStatusMap() // Update map whenever medication list changes
        }.launchIn(scope)

        // This part needs adjustment based on how records are fetched.
        // For simplicity, we'll manually refresh records when needed for now.
    }

    private fun MedicationEntity.toDomainModel(): Medication {
        val type = object : TypeToken<Map<Int, Long>>() {}.type
        val timesMap: Map<Int, Long> = gson.fromJson(this.times, type)
        return Medication(
            id = this.id,
            name = this.name,
            dosage = this.dosage,
            startDate = this.startDate,
            endDate = this.endDate,
            times = timesMap,
            slotNumber = this.slotNumber,
            totalPills = this.totalPills,
            remainingPills = this.remainingPills,
            reminderThreshold = this.reminderThreshold
        )
    }

    private fun Medication.toEntity(): MedicationEntity {
        return MedicationEntity(
            id = this.id,
            name = this.name,
            dosage = this.dosage,
            startDate = this.startDate,
            endDate = this.endDate,
            times = gson.toJson(this.times),
            slotNumber = this.slotNumber,
            totalPills = this.totalPills,
            remainingPills = this.remainingPills,
            reminderThreshold = this.reminderThreshold
        )
    }


    // --- Medication Management ---

    fun addMedications(newMedications: List<Medication>) {
         scope.launch {
            newMedications.forEach { medication ->
                medicationDao.insertMedication(medication.toEntity())
            }
        }
    }

    fun updateMedication(updatedMed: Medication) {
        scope.launch {
            medicationDao.updateMedication(updatedMed.toEntity())
        }
    }

    fun deleteMedication(medToDelete: Medication) {
        scope.launch {
            medicationDao.deleteMedicationById(medToDelete.id)
        }
    }

    fun processMedicationTaken(slotNumber: Int) {
        scope.launch {
            val medication = _medicationList.value.firstOrNull { it.slotNumber == slotNumber }
            medication?.let {
                if (it.remainingPills > 0) {
                    val updatedMed = it.copy(remainingPills = it.remainingPills - 1)
                    medicationDao.updateMedication(updatedMed.toEntity())
                    takenRecordDao.insertTakenRecord(TakenRecordEntity(medicationId = it.id, takenTimestamp = System.currentTimeMillis()))

                    if (updatedMed.remainingPills <= updatedMed.reminderThreshold) {
                        sendLowStockNotification(updatedMed)
                    }

                    // Refresh taken records manually after adding a new one
                    loadTakenRecordsForDateRange(it.id)
                }
            }
        }
    }

    private fun sendLowStockNotification(medication: Medication) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "low_stock_channel"

        val channel = NotificationChannel(channelId, "Low Stock Warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for when medication stock is running low"
            enableVibration(true)
            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)

        val title = context.getString(R.string.low_stock_warning_title)
        val message = context.getString(R.string.low_stock_warning_message, medication.name, medication.remainingPills)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(medication.id, notification)
    }
    
    private suspend fun loadTakenRecordsForDateRange(medicationId: Int) {
        val records = takenRecordDao.getRecordsForMedication(medicationId).first()
        _takenRecords.value = records.map { MedicationTakenRecord(it.takenTimestamp) }
        updateDailyStatusMap()
    }


    private fun updateDailyStatusMap() {
        val statusMap = mutableMapOf<String, Int>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val medications = _medicationList.value
        val records = _takenRecords.value

        if (medications.isEmpty()) {
            _dailyStatusMap.value = emptyMap()
            updateComplianceRate(emptyMap())
            return
        }

        val minStartDate = medications.minOf { it.startDate }
        val maxEndDate = medications.maxOf { it.endDate }

        calendar.timeInMillis = minStartDate
        val endDateCalendar = Calendar.getInstance()
        endDateCalendar.timeInMillis = maxEndDate

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 23)
        today.set(Calendar.MINUTE, 59)
        today.set(Calendar.SECOND, 59)

        while (calendar.before(endDateCalendar) || isSameDay(calendar, endDateCalendar)) {
            val date = calendar.time
            val dateStr = sdf.format(date)
            val dateTimestamp = date.time

            val medsForThisDay = medications.filter { dateTimestamp >= it.startDate && dateTimestamp <= it.endDate }

            if (medsForThisDay.isEmpty()) {
                statusMap[dateStr] = STATUS_NOT_APPLICABLE
            } else {
                val totalDosesForDay = medsForThisDay.sumOf { it.times.size }
                val takenDosesForDay = records.count {
                    val recordCal = Calendar.getInstance()
                    recordCal.timeInMillis = it.timestamp
                    isSameDay(recordCal, calendar)
                }

                statusMap[dateStr] = when {
                    takenDosesForDay == 0 && calendar.before(today) -> STATUS_NONE_TAKEN
                    takenDosesForDay < totalDosesForDay && calendar.before(today) -> STATUS_PARTIALLY_TAKEN
                    takenDosesForDay >= totalDosesForDay -> STATUS_ALL_TAKEN
                    else -> STATUS_NOT_APPLICABLE // For future dates
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        _dailyStatusMap.value = statusMap
        updateComplianceRate(statusMap)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun updateComplianceRate(status: Map<String, Int>) {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var takenCount = 0
        var applicableDays = 0

        repeat(30) {
            val dateStr = sdf.format(calendar.time)
            when (status[dateStr]) {
                STATUS_ALL_TAKEN -> {
                    takenCount++
                    applicableDays++
                }
                STATUS_PARTIALLY_TAKEN, STATUS_NONE_TAKEN -> {
                    applicableDays++
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        _complianceRate.value = if (applicableDays > 0) {
            takenCount.toFloat() / applicableDays.toFloat()
        } else {
            0f
        }
    }


    // --- Sensor Data Management ---

    fun clearHistoricData() {
        historicDataBuffer.clear()
        _historicSensorData.value = emptyList()
    }

    fun addSensorDataPoint(temperature: Float, humidity: Float) {
        val now = System.currentTimeMillis() / 1000
        val newDataPoint = SensorDataPoint(now, temperature, humidity)
        val currentList = _historicSensorData.value.toMutableList()
        currentList.add(newDataPoint)
        currentList.sortBy { it.timestamp }
        _historicSensorData.value = currentList
    }
    
    fun bufferHistoricData(timestamp: Long, temperature: Float, humidity: Float) {
        historicDataBuffer.add(SensorDataPoint(timestamp, temperature, humidity))
    }

    fun commitHistoricDataBuffer() {
        val sortedData = historicDataBuffer.sortedBy { it.timestamp }.toList()
        _historicSensorData.value = sortedData
    }

    fun setEngineeringMode(isEnabled: Boolean) {
        _isEngineeringMode.value = isEnabled
        sharedPreferences.edit { putBoolean(KEY_ENGINEERING_MODE, isEnabled) }
    }

    // --- Persistence & Migration ---

    private suspend fun checkAndMigrateData() {
        val isMigrationDone = sharedPreferences.getBoolean(KEY_MIGRATION_DONE, false)
        if (!isMigrationDone) {
            Log.i("AppRepository", "Starting data migration from SharedPreferences to Room.")

            // Migrate Medications
            val medJson = sharedPreferences.getString(KEY_MEDICATION_DATA, null)
            if (medJson != null) {
                try {
                    val oldMeds: List<Medication> = gson.fromJson(medJson, object : TypeToken<List<Medication>>() {}.type)
                    oldMeds.forEach { medication ->
                        medicationDao.insertMedication(medication.toEntity())
                    }
                    Log.i("AppRepository", "Migrated ${oldMeds.size} medications.")
                } catch (e: JsonSyntaxException) {
                    Log.e("AppRepository", "Failed to parse old medication data during migration.", e)
                }
            }

            // Migrate Taken Records
            val recordsJson = sharedPreferences.getString(KEY_TAKEN_RECORDS, null)
            if (recordsJson != null) {
                try {
                    val oldRecords: List<MedicationTakenRecord> = gson.fromJson(recordsJson, object : TypeToken<List<MedicationTakenRecord>>() {}.type)
                    // This is tricky because old records don't have a medication ID.
                    // We will skip migrating records for simplicity.
                    Log.w("AppRepository", "Skipping migration of ${oldRecords.size} taken records due to missing medication ID.")
                } catch (e: JsonSyntaxException) {
                     Log.e("AppRepository", "Failed to parse old taken records during migration.", e)
                }
            }

            sharedPreferences.edit {
                putBoolean(KEY_MIGRATION_DONE, true)
                // Optionally, remove old data after successful migration
                // remove(KEY_MEDICATION_DATA)
                // remove(KEY_TAKEN_RECORDS)
            }
            Log.i("AppRepository", "Data migration finished.")
        }
    }

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_TAKEN_RECORDS = "taken_records"
        const val KEY_ENGINEERING_MODE = "engineering_mode"
        const val KEY_MIGRATION_DONE = "migration_to_room_done"
        
        const val STATUS_NOT_APPLICABLE = 0
        const val STATUS_NONE_TAKEN = 1
        const val STATUS_ALL_TAKEN = 2
        const val STATUS_PARTIALLY_TAKEN = 3
    }
}
