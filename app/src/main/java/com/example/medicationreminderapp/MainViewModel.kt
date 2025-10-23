package com.example.medicationreminderapp

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData for UI state
    val isBleConnected = MutableLiveData<Boolean>(false)
    val bleStatus = MutableLiveData<String>("Disconnected")
    val temperature = MutableLiveData<Float>(0.0f)
    val humidity = MutableLiveData<Float>(0.0f)

    // LiveData for App Data
    val medicationList = MutableLiveData<MutableList<Medication>>(mutableListOf())
    val dailyStatusMap = MutableLiveData<MutableMap<String, Int>>(mutableMapOf())
    val notesMap = MutableLiveData<MutableMap<String, String>>(mutableMapOf())
    val complianceRate = MutableLiveData<Float>(0f)

    private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadAllData()
    }

    fun addMedications(newMedications: List<Medication>) {
        val currentList = medicationList.value ?: mutableListOf()
        currentList.addAll(newMedications)
        medicationList.value = currentList
        saveMedicationData() // Save the updated list
    }

    fun onGuidedFillConfirmed() {
        // TODO: Implement logic to proceed to the next step in guided filling
        Log.d("MainViewModel", "Guided fill confirmed.")
    }

    fun processMedicationTaken(slotNumber: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newStatusMap = dailyStatusMap.value ?: mutableMapOf()
        newStatusMap[todayStr] = STATUS_ALL_TAKEN
        dailyStatusMap.value = newStatusMap // This is fine as it's re-assigning a new map

        val currentMeds = medicationList.value ?: return // Get current list or exit if null
        val med = currentMeds.find { it.slotNumber == slotNumber }
        med?.let {
            if (it.remainingPills > 0) {
                it.remainingPills--
            }
            // Notify observer that the list content has changed
            medicationList.value = currentMeds
        }

        saveMedicationData()
        saveDailyStatusData()
        updateComplianceRate(currentMeds, newStatusMap)
    }

    fun updateComplianceRate(meds: List<Medication>, status: Map<String, Int>) {
        // TODO: Implement the compliance rate calculation logic here
        complianceRate.value = 0.5f // Placeholder value
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

    private fun saveNotesData() {
        sharedPreferences.edit {
            putString(KEY_NOTES_DATA, gson.toJson(notesMap.value))
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

    companion object {
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_NOTES_DATA = "notes_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
    }
}
