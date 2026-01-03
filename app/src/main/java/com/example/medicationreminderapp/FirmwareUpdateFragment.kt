package com.example.medicationreminderapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.medicationreminderapp.databinding.FragmentFirmwareUpdateBinding

class FirmwareUpdateFragment : Fragment(), BluetoothLeManager.BleListener {

    private var _binding: FragmentFirmwareUpdateBinding? = null
    private val binding get() = _binding!!

    private var selectedFirmwareUri: Uri? = null

    private val bluetoothLeManager: BluetoothLeManager? by lazy {
        (activity as? MainActivity)?.bluetoothLeManager
    }

    private val selectFirmwareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFirmwareUri = uri
                binding.firmwareFileName.text = uri.lastPathSegment
                binding.startUpdateButton.isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirmwareUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bluetoothLeManager?.listener = this

        binding.selectFirmwareButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*" // Allow all file types for now, will filter for .bin
            }
            selectFirmwareLauncher.launch(intent)
        }

        binding.startUpdateButton.setOnClickListener {
            selectedFirmwareUri?.let { uri ->
                val firmwareBytes = requireContext().contentResolver.openInputStream(uri)?.readBytes()
                firmwareBytes?.let {
                    bluetoothLeManager?.startOtaUpdate(it)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothLeManager?.listener = null
        _binding = null
    }

    override fun onStatusUpdate(messageResId: Int, vararg formatArgs: Any) {}

    override fun onDeviceConnected() {}

    override fun onDeviceDisconnected() {}

    override fun onReconnectStarted() {}

    override fun onReconnectFailed() {}

    override fun onProtocolVersionReported(version: Int) {}

    override fun onMedicationTaken(slotNumber: Int) {}

    override fun onBoxStatusUpdate(slotMask: Byte) {}

    override fun onTimeSyncAcknowledged() {}

    override fun onEngineeringModeUpdate(isEngineeringMode: Boolean) {}

    override fun onSensorData(temperature: Float, humidity: Float) {}

    override fun onHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float) {}

    override fun onHistoricDataComplete() {}

    override fun onWifiStatusUpdate(status: Int) {}

    override fun onError(errorCode: Int) {}

    override fun onOtaProgressUpdate(progress: Int) {
        binding.updateProgressBar.progress = progress
    }
}
