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
// Removed explicit import for SensorDataPoint as it's in the same package

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Data StateFlows
    private val _medicationList = MutableStateFlow<List<Medication>>(emptyList())
    val medicationList: StateFlow<List<Medication>> = _medicationList.asStateFlow()

    private val _dailyStatusMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyStatusMap: StateFlow<Map<String, Int>> = _dailyStatusMap.asStateFlow()

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
    }

    fun updateMedication(updatedMed: Medication) {
        val currentList = _medicationList.value
        _medicationList.value = currentList.map {
            if (it.slotNumber == updatedMed.slotNumber) updatedMed else it
        }
        saveMedicationData()
    }

    fun deleteMedication(medToDelete: Medication) {
        val currentList = _medicationList.value
        _medicationList.value = currentList.filter { it.slotNumber != medToDelete.slotNumber }
        saveMedicationData()
    }

    fun processMedicationTaken(slotNumber: Int) {
        scope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val newStatusMap = _dailyStatusMap.value.toMutableMap()
            newStatusMap[todayStr] = STATUS_ALL_TAKEN
            _dailyStatusMap.value = newStatusMap

            val updatedList = _medicationList.value.map {
                if (it.slotNumber == slotNumber && it.remainingPills > 0) {
                    it.copy(remainingPills = it.remainingPills - 1)
                } else {
                    it
                }
            }
            _medicationList.value = updatedList

            saveMedicationData()
            saveDailyStatusData()
            updateComplianceRate(newStatusMap)
        }
    }

    private fun updateComplianceRate(status: Map<String, Int>) {
        // Calculate compliance for the past 30 days
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var takenCount = 0
        
        // Iterate backwards from today for 30 days
        for (i in 0 until 30) {
            val dateStr = sdf.format(calendar.time)
            if (status[dateStr] == STATUS_ALL_TAKEN) {
                takenCount++
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        _complianceRate.value = (takenCount.toFloat() / 30f)
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
        loadDailyStatusData()
        updateComplianceRate(_dailyStatusMap.value)
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

    private fun saveDailyStatusData() {
        sharedPreferences.edit {
            putString(KEY_DAILY_STATUS, gson.toJson(_dailyStatusMap.value))
        }
    }

    private fun loadDailyStatusData() {
        sharedPreferences.getString(KEY_DAILY_STATUS, null)?.let {
            try {
                val data: Map<String, Int> = gson.fromJson(it, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                _dailyStatusMap.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("AppRepository", "Failed to parse daily status data", e)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
    }
}
