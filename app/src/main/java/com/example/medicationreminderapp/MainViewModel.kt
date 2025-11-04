package com.example.medicationreminderapp

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.medicationreminderapp.util.SingleLiveEvent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class SensorDataPoint(val timestamp: Long, val temperature: Float, val humidity: Float)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData for UI state
    val isBleConnected = MutableLiveData<Boolean>(false)
    val bleStatus = MutableLiveData<String>("Disconnected")

    // LiveData for Sensor Data
    private val _historicSensorData = MutableLiveData<List<SensorDataPoint>>(emptyList())
    val historicSensorData: LiveData<List<SensorDataPoint>> = _historicSensorData

    private val historicDataBuffer = mutableListOf<SensorDataPoint>()

    // LiveData for App Data
    val medicationList = MutableLiveData<MutableList<Medication>>(mutableListOf())
    val dailyStatusMap = MutableLiveData<MutableMap<String, Int>>(mutableMapOf())
    val notesMap = MutableLiveData<MutableMap<String, String>>(mutableMapOf())
    val complianceRate = MutableLiveData<Float>(0f)

    // Event for triggering BLE actions in Activity
    val requestBleAction = SingleLiveEvent<BleAction>()

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadAllData()
    }

    fun onRefreshEnvironmentData() {
        historicDataBuffer.clear()
        _historicSensorData.value = emptyList() // Clear the UI
        requestBleAction.value = BleAction.REQUEST_HISTORIC_ENV_DATA
    }

    // Called from MainActivity when a new real-time data point arrives
    fun onNewSensorData(temperature: Float, humidity: Float) {
        val now = System.currentTimeMillis() / 1000
        val newDataPoint = SensorDataPoint(now, temperature, humidity)
        _historicSensorData.value = (_historicSensorData.value ?: emptyList()) + newDataPoint
    }

    // Called from MainActivity to buffer historic data points
    fun addHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float) {
        historicDataBuffer.add(SensorDataPoint(timestamp, temperature, humidity))
    }

    // Called from MainActivity when all historic data has been received
    fun onHistoricDataSyncCompleted() {
        _historicSensorData.value = historicDataBuffer.sortedBy { it.timestamp }.toList()
        bleStatus.value = "Historic data sync complete"
    }

    fun addMedications(newMedications: List<Medication>) {
        val currentList = medicationList.value ?: mutableListOf()
        val newList = currentList.toMutableList().apply {
            addAll(newMedications)
        }
        medicationList.value = newList
        saveMedicationData() // Save the updated list
    }

    fun updateMedication(updatedMed: Medication) {
        val currentList = medicationList.value ?: return
        val newList = currentList.map {
            if (it.slotNumber == updatedMed.slotNumber) {
                updatedMed
            } else {
                it
            }
        }.toMutableList()
        medicationList.value = newList
        saveMedicationData()
    }

    fun deleteMedication(medToDelete: Medication) {
        val currentList = medicationList.value ?: return
        val newList = currentList.filter {
            it.slotNumber != medToDelete.slotNumber
        }.toMutableList()
        medicationList.value = newList
        saveMedicationData()
    }

    fun processMedicationTaken(slotNumber: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newStatusMap = dailyStatusMap.value ?: mutableMapOf()
        newStatusMap[todayStr] = STATUS_ALL_TAKEN
        dailyStatusMap.value = newStatusMap // This is fine as it's re-assigning a new map

        val currentMeds = medicationList.value ?: return // Get current list or exit if null
        val updatedList = currentMeds.map {
            if (it.slotNumber == slotNumber && it.remainingPills > 0) {
                it.copy(remainingPills = it.remainingPills - 1)
            } else {
                it
            }
        }.toMutableList()

        medicationList.value = updatedList


        saveMedicationData()
        saveDailyStatusData()
        updateComplianceRate(newStatusMap)
    }

    fun updateComplianceRate(status: Map<String, Int>) {
        val totalDays = status.keys.size
        if (totalDays == 0) {
            complianceRate.value = 0f
            return
        }

        val daysTaken = status.values.count { it == STATUS_ALL_TAKEN }
        val rate = (daysTaken.toFloat() / totalDays.toFloat())
        complianceRate.value = rate
    }

    // --- Data Persistence ---

    private fun loadAllData() {
        loadMedicationData()
        loadNotesData()
        loadDailyStatusData()
    }

    private fun saveMedicationData() {
        sharedPreferences.edit {
            putString(KEY_MEDICATION_DATA, gson.toJson(medicationList.value))
        }
    }

    private fun loadMedicationData() {
        sharedPreferences.getString(KEY_MEDICATION_DATA, null)?.let {
            try {
                val data: MutableList<Medication> = gson.fromJson(it, object : TypeToken<MutableList<Medication>>() {}.type) ?: mutableListOf()
                medicationList.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("MainViewModel", "Failed to parse medication data", e)
            }
        }
    }

    private fun loadNotesData() {
        sharedPreferences.getString(KEY_NOTES_DATA, null)?.let {
            try {
                val data: MutableMap<String, String> = gson.fromJson(it, object : TypeToken<MutableMap<String, String>>() {}.type) ?: mutableMapOf()
                notesMap.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("MainViewModel", "Failed to parse notes data", e)
            }
        }
    }

    private fun saveDailyStatusData() {
        sharedPreferences.edit {
            putString(KEY_DAILY_STATUS, gson.toJson(dailyStatusMap.value))
        }
    }

    private fun loadDailyStatusData() {
        sharedPreferences.getString(KEY_DAILY_STATUS, null)?.let {
            try {
                val data: MutableMap<String, Int> = gson.fromJson(it, object : TypeToken<MutableMap<String, Int>>() {}.type) ?: mutableMapOf()
                dailyStatusMap.value = data
            } catch (e: JsonSyntaxException) {
                Log.e("MainViewModel", "Failed to parse daily status data", e)
            }
        }
    }

    enum class BleAction {
        REQUEST_ENV_DATA,
        REQUEST_HISTORIC_ENV_DATA
    }

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_NOTES_DATA = "notes_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
    }
}
