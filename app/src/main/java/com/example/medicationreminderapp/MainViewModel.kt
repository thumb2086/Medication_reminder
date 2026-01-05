package com.example.medicationreminderapp

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicationreminderapp.util.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    // --- UI State for BLE Connection ---
    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected.asStateFlow()

    private val _bleStatus = MutableStateFlow(R.string.disconnected)
    val bleStatus: StateFlow<Int> = _bleStatus.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    // --- Delegated Data StateFlows from Repository ---
    val isEngineeringMode: StateFlow<Boolean> = repository.isEngineeringMode
    val historicSensorData: StateFlow<List<SensorDataPoint>> = repository.historicSensorData
    val medicationList: StateFlow<List<Medication>> = repository.medicationList
    val dailyStatusMap: StateFlow<Map<String, Int>> = repository.dailyStatusMap

    private val _reportComplianceData = MutableStateFlow<List<ComplianceDataPoint>>(emptyList())
    val reportComplianceData: StateFlow<List<ComplianceDataPoint>> = _reportComplianceData.asStateFlow()

    private val _complianceRateText = MutableStateFlow("")
    val complianceRateText: StateFlow<String> = _complianceRateText.asStateFlow()


    // Event for triggering BLE actions in Activity/Fragment
    val requestBleAction = SingleLiveEvent<BleAction>()

    init {
        // Data loading is handled by Repository's init
        viewModelScope.launch {
            repository.medicationList.combine(repository.complianceRate) { meds, rate ->
                if (meds.isEmpty()) {
                    "無紀錄"
                } else {
                    val percentage = (rate * 100).toInt()
                    "服藥正確率${percentage}%"
                }
            }.collect {
                _complianceRateText.value = it
            }
        }
    }

    fun calculateComplianceRateForTimeframe(timeframe: Timeframe) {
        viewModelScope.launch {
            val data = repository.getComplianceDataForTimeframe(timeframe)
            _reportComplianceData.value = data
        }
    }

    // --- Helper to get build-time constants ---
    fun getCurrentUpdateChannel(): String {
        return BuildConfig.UPDATE_CHANNEL
    }

    // --- Public Methods to Update State from UI/Service ---

    fun setBleConnectionState(isConnected: Boolean) {
        _isBleConnected.value = isConnected
        if (isConnected) {
            _isReconnecting.value = false // Successfully connected, stop showing reconnecting UI
        } else {
            // If not connected and not in the process of reconnecting, show disconnected.
            if (!_isReconnecting.value) {
                _bleStatus.value = R.string.disconnected
            }
        }
    }

    fun setBleStatus(@StringRes statusResId: Int) {
        _bleStatus.value = statusResId
    }

    fun onReconnectStarted() {
        _isReconnecting.value = true
        _isBleConnected.value = false
        _bleStatus.value = R.string.ble_status_reconnecting
    }

    fun onReconnectFailed() {
        _isReconnecting.value = false
        _isBleConnected.value = false
        _bleStatus.value = R.string.ble_status_reconnect_failed
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
        repository.processMedicationTaken(slotNumber)
    }

    // Enum to represent BLE actions requested by the ViewModel
    enum class BleAction {
        REQUEST_ENV_DATA,
        REQUEST_HISTORIC_ENV_DATA
    }
}
