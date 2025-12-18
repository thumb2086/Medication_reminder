package com.example.medicationreminderapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class MedicationTakenRecord(val timestamp: Long)

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context
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

    private val historicDataBuffer = mutableListOf<SensorDataPoint>()

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO) // Repository scope for IO operations

    init {
        loadAllData()
    }

    // --- Medication Management ---

    fun addMedications(newMedications: List<Medication>) {
        val currentList = _medicationList.value
        _medicationList.value = currentList + newMedications
        saveMedicationData()
        updateDailyStatusMap()
    }

    fun updateMedication(updatedMed: Medication) {
        val currentList = _medicationList.value
        _medicationList.value = currentList.map {
            if (it.slotNumber == updatedMed.slotNumber) updatedMed else it
        }
        saveMedicationData()
        updateDailyStatusMap()
    }

    fun deleteMedication(medToDelete: Medication) {
        val currentList = _medicationList.value
        _medicationList.value = currentList.filter { it.slotNumber != medToDelete.slotNumber }
        saveMedicationData()
        updateDailyStatusMap()
    }

    fun processMedicationTaken(slotNumber: Int) {
        scope.launch {
            val updatedList = _medicationList.value.map {
                if (it.slotNumber == slotNumber && it.remainingPills > 0) {
                    it.copy(remainingPills = it.remainingPills - 1)
                } else {
                    it
                }
            }
            _medicationList.value = updatedList
            _takenRecords.value = _takenRecords.value + MedicationTakenRecord(System.currentTimeMillis())

            saveMedicationData()
            saveTakenRecords()
            updateDailyStatusMap()
        }
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

        val minStartDate = medications.minOfOrNull { it.startDate } ?: System.currentTimeMillis()
        val maxEndDate = medications.maxOfOrNull { it.endDate } ?: System.currentTimeMillis()

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

    // --- Persistence ---

    private fun loadAllData() {
        loadMedicationData()
        loadTakenRecords()
        updateDailyStatusMap()
    }

    private fun saveMedicationData() {
        sharedPreferences.edit {
            putString(KEY_MEDICATION_DATA, gson.toJson(_medicationList.value))
        }
    }

    private fun loadMedicationData() {
        sharedPreferences.getString(KEY_MEDICATION_DATA, null)?.let {
            try {
                val data: List<Medication> = gson.fromJson(it, object : TypeToken<List<Medication>>() {}.type) ?: emptyList()
                _medicationList.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("AppRepository", "Failed to parse medication data", e)
            }
        }
    }
    
    private fun saveTakenRecords() {
        sharedPreferences.edit {
            putString(KEY_TAKEN_RECORDS, gson.toJson(_takenRecords.value))
        }
    }

    private fun loadTakenRecords() {
        sharedPreferences.getString(KEY_TAKEN_RECORDS, null)?.let {
            try {
                val data: List<MedicationTakenRecord> = gson.fromJson(it, object : TypeToken<List<MedicationTakenRecord>>() {}.type) ?: emptyList()
                _takenRecords.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("AppRepository", "Failed to parse taken records", e)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_TAKEN_RECORDS = "taken_records"
        
        const val STATUS_NOT_APPLICABLE = 0
        const val STATUS_NONE_TAKEN = 1
        const val STATUS_ALL_TAKEN = 2
        const val STATUS_PARTIALLY_TAKEN = 3
    }
}
