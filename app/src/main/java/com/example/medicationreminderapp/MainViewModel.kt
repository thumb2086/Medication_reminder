package com.example.medicationreminderapp

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

    private val _bleStatus = MutableStateFlow("Disconnected")
    val bleStatus: StateFlow<String> = _bleStatus.asStateFlow()

    private val _isEngineeringMode = MutableStateFlow(false)
    val isEngineeringMode: StateFlow<Boolean> = _isEngineeringMode.asStateFlow()

    // Delegate Data StateFlows to Repository
    val historicSensorData: StateFlow<List<SensorDataPoint>> = repository.historicSensorData
    val medicationList: StateFlow<List<Medication>> = repository.medicationList
    val dailyStatusMap: StateFlow<Map<String, Int>> = repository.dailyStatusMap
    val complianceRate: StateFlow<Float> = repository.complianceRate

    // Event for triggering BLE actions in Activity
    val requestBleAction = SingleLiveEvent<BleAction>()

    init {
        // Data loading is now handled by Repository's init
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
        _bleStatus.value = "Historic data sync complete"
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
