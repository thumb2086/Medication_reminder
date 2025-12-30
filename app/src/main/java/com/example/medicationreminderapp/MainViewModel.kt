package com.example.medicationreminderapp

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicationreminderapp.util.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    // StateFlow for UI state (BLE logic remains in ViewModel as it's transient UI state)
    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected.asStateFlow()

    private val _bleStatus = MutableStateFlow(R.string.disconnected)
    val bleStatus: StateFlow<Int> = _bleStatus.asStateFlow()

    // Delegate Data StateFlows to Repository
    val isEngineeringMode: StateFlow<Boolean> = repository.isEngineeringMode
    val historicSensorData: StateFlow<List<SensorDataPoint>> = repository.historicSensorData
    val medicationList: StateFlow<List<Medication>> = repository.medicationList
    val dailyStatusMap: StateFlow<Map<String, Int>> = repository.dailyStatusMap
    val complianceRate: StateFlow<Float> = repository.complianceRate

    // Event for triggering BLE actions in Activity
    val requestBleAction = SingleLiveEvent<BleAction>()

    init {
        // Data loading is now handled by Repository's init
    }

    // --- Helper to avoid constant expression warnings in UI ---
    fun getCurrentUpdateChannel(): String {
        return BuildConfig.UPDATE_CHANNEL
    }

    // --- Public Methods to update state ---

    fun setBleConnectionState(isConnected: Boolean) {
        _isBleConnected.value = isConnected
        if (!isConnected) {
            _bleStatus.value = R.string.disconnected
        }
    }

    fun setBleStatus(@StringRes statusResId: Int) {
        _bleStatus.value = statusResId
    }

    fun setEngineeringMode(isEnabled: Boolean) {
        repository.setEngineeringMode(isEnabled)
    }

    fun onRefreshEnvironmentData() {
        repository.clearHistoricData()
        requestBleAction.value = BleAction.REQUEST_HISTORIC_ENV_DATA
    }

    fun onNewSensorData(temperature: Float, humidity: Float) {
        repository.addSensorDataPoint(temperature, humidity)
    }

    fun addHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float) {
        repository.bufferHistoricData(timestamp, temperature, humidity)
    }

    fun onHistoricDataSyncCompleted() {
        repository.commitHistoricDataBuffer()
        // This should be a string resource
        // _bleStatus.value = "Historic data sync complete"
    }

    fun addMedications(newMedications: List<Medication>) {
        viewModelScope.launch {
            repository.addMedications(newMedications)
        }
    }

    fun updateMedication(updatedMed: Medication) {
        viewModelScope.launch {
            repository.updateMedication(updatedMed)
        }
    }

    fun deleteMedication(medToDelete: Medication) {
        viewModelScope.launch {
            repository.deleteMedication(medToDelete)
        }
    }

    fun processMedicationTaken(slotNumber: Int) {
        // This can still be called from UI if needed, delegating to repo
        repository.processMedicationTaken(slotNumber)
    }

    enum class BleAction {
        REQUEST_ENV_DATA,
        REQUEST_HISTORIC_ENV_DATA
    }
}
