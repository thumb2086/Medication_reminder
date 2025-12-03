package com.example.medicationreminderapp

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicationreminderapp.util.SingleLiveEvent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

data class SensorDataPoint(val timestamp: Long, val temperature: Float, val humidity: Float)

@HiltViewModel
class MainViewModel @Inject constructor(application: Application) : ViewModel() {

    // StateFlow for UI state
    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected.asStateFlow()

    private val _bleStatus = MutableStateFlow("Disconnected")
    val bleStatus: StateFlow<String> = _bleStatus.asStateFlow()

    private val _isEngineeringMode = MutableStateFlow(false)
    val isEngineeringMode: StateFlow<Boolean> = _isEngineeringMode.asStateFlow()

    // StateFlow for Sensor Data
    private val _historicSensorData = MutableStateFlow<List<SensorDataPoint>>(emptyList())
    val historicSensorData: StateFlow<List<SensorDataPoint>> = _historicSensorData.asStateFlow()

    private val historicDataBuffer = mutableListOf<SensorDataPoint>()

    // StateFlow for App Data
    private val _medicationList = MutableStateFlow<List<Medication>>(emptyList())
    val medicationList: StateFlow<List<Medication>> = _medicationList.asStateFlow()

    private val _dailyStatusMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyStatusMap: StateFlow<Map<String, Int>> = _dailyStatusMap.asStateFlow()

    private val _complianceRate = MutableStateFlow(0f)
    val complianceRate: StateFlow<Float> = _complianceRate.asStateFlow()

    // Event for triggering BLE actions in Activity (remains as SingleLiveEvent for simplicity)
    val requestBleAction = SingleLiveEvent<BleAction>()

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val gson = Gson()

    init {
        viewModelScope.launch {
            loadAllData()
            // TODO: Remove this after testing chart
            loadSimulatedData()
        }
    }

    // --- Public Methods to update state ---

    fun setBleConnectionState(isConnected: Boolean) {
        _isBleConnected.value = isConnected
        if (!isConnected) {
            _bleStatus.value = "Disconnected"
        }
    }

    fun setBleStatus(status: String) {
        _bleStatus.value = status
    }

    fun setEngineeringMode(isEnabled: Boolean) {
        _isEngineeringMode.value = isEnabled
    }

    fun onRefreshEnvironmentData() {
        historicDataBuffer.clear()
        _historicSensorData.value = emptyList() // Clear the UI
        requestBleAction.value = BleAction.REQUEST_HISTORIC_ENV_DATA
    }

    fun onNewSensorData(temperature: Float, humidity: Float) {
        val now = System.currentTimeMillis() / 1000
        val newDataPoint = SensorDataPoint(now, temperature, humidity)
        // Append to historic list if needed or handle separately for real-time display
        // Currently we merge it into the historic list for simplicity in graph viewing
        val currentList = _historicSensorData.value.toMutableList()
        currentList.add(newDataPoint)
        currentList.sortBy { it.timestamp }
        _historicSensorData.value = currentList
    }

    fun addHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float) {
        historicDataBuffer.add(SensorDataPoint(timestamp, temperature, humidity))
    }

    fun onHistoricDataSyncCompleted() {
        val sortedData = historicDataBuffer.sortedBy { it.timestamp }.toList()
        _historicSensorData.value = sortedData
        _bleStatus.value = "Historic data sync complete"
    }

    fun addMedications(newMedications: List<Medication>) {
        viewModelScope.launch {
            val currentList = _medicationList.value
            _medicationList.value = currentList + newMedications
            saveMedicationData()
        }
    }

    fun updateMedication(updatedMed: Medication) {
        viewModelScope.launch {
            val currentList = _medicationList.value
            _medicationList.value = currentList.map {
                if (it.slotNumber == updatedMed.slotNumber) updatedMed else it
            }
            saveMedicationData()
        }
    }

    fun deleteMedication(medToDelete: Medication) {
        viewModelScope.launch {
            val currentList = _medicationList.value
            _medicationList.value = currentList.filter { it.slotNumber != medToDelete.slotNumber }
            saveMedicationData()
        }
    }

    fun processMedicationTaken(slotNumber: Int) {
        viewModelScope.launch {
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
        val totalDays = status.keys.size
        if (totalDays == 0) {
            _complianceRate.value = 0f
            return
        }
        val daysTaken = status.values.count { it == STATUS_ALL_TAKEN }
        _complianceRate.value = (daysTaken.toFloat() / totalDays.toFloat())
    }

    // --- Data Persistence (should be run in a coroutine) ---

    private fun loadAllData() {
        loadMedicationData()
        loadDailyStatusData()
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
                Log.e("MainViewModel", "Failed to parse medication data", e)
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
                Log.e("MainViewModel", "Failed to parse daily status data", e)
            }
        }
    }

    // TODO: Remove this function after testing
    private fun loadSimulatedData() {
        val now = System.currentTimeMillis() / 1000
        val fakeData = mutableListOf<SensorDataPoint>()
        // Generate 20 points, one every 5 minutes for the last 100 minutes
        for (i in 0 until 20) {
            val time = now - (19 - i) * 300 // 300 seconds = 5 minutes
            // Temp varies around 25C (22-28)
            val temp = 25f + (sin(i.toDouble() / 3.0).toFloat() * 3f) + ((Math.random() - 0.5).toFloat() * 1f)
            // Humidity varies around 60% (50-70)
            val hum = 60f + (cos(i.toDouble() / 3.0).toFloat() * 10f) + ((Math.random() - 0.5).toFloat() * 5f)
            fakeData.add(SensorDataPoint(time, temp, hum))
        }
        _historicSensorData.value = fakeData
        _isBleConnected.value = true // Force UI to show chart
    }

    enum class BleAction {
        REQUEST_ENV_DATA,
        REQUEST_HISTORIC_ENV_DATA
    }

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
    }
}
