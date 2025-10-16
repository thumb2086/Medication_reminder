package com.example.medicationreminderapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.Calendar
import java.util.UUID

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class BluetoothLeManager(private val context: Context, private val listener: BleListener) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                handler.post { listener.onStatusUpdate("已連接，正在搜尋服務...") }
                bluetoothGatt = gatt
                handler.post { gatt.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                gatt.close()
                bluetoothGatt = null
                handler.post { listener.onDeviceDisconnected() }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                if (service == null) {
                    Log.w(TAG, "Service not found!")
                    handler.post { listener.onStatusUpdate("服務未找到") }
                    disconnect()
                    return
                }
                commandCharacteristic = service.getCharacteristic(UUID.fromString(COMMAND_CHANNEL_UUID))
                val dataEventCharacteristic = service.getCharacteristic(UUID.fromString(DATA_EVENT_CHANNEL_UUID))

                if (commandCharacteristic == null || dataEventCharacteristic == null) {
                    Log.w(TAG, "One or more characteristics not found!")
                    handler.post { listener.onStatusUpdate("特徵碼未找到") }
                    disconnect()
                    return
                }
                
                gatt.setCharacteristicNotification(dataEventCharacteristic, true)
                val descriptor = dataEventCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)

                handler.post { listener.onDeviceConnected() }

            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
                handler.post { listener.onStatusUpdate("服務搜尋失敗: $status") }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                Log.d(TAG, "Received data: ${data.joinToString { "%02X".format(it) }}")
                when (data[0].toInt() and 0xFF) {
                    EVT_MEDICATION_TAKEN -> {
                        if (data.size >= 2) {
                            val slot = data[1].toInt()
                            handler.post { listener.onMedicationTaken(slot) }
                        }
                    }
                    EVT_TIME_SYNC_ACK -> {
                        Log.d(TAG, "Time Sync ACK received from device.")
                    }
                    EVT_BOX_STATUS_UPDATE -> {
                         if (data.size >= 2) {
                             val slotMask = data[1]
                             handler.post { listener.onBoxStatusUpdate(slotMask) }
                         }
                    }
                }
            }
        }
    }
    
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "Scan Result: ${result.device.name ?: "Unknown"}")
            stopScan()
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan Failed: Error Code: $errorCode")
            isScanning = false
            handler.post { listener.onStatusUpdate("掃描失敗，代碼: $errorCode") }
        }
    }

    fun startScan() {
        if (isScanning) return
        if (bluetoothAdapter?.isEnabled == false) {
            listener.onStatusUpdate("藍牙未啟用")
            return
        }
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        handler.postDelayed({
            if (isScanning) {
                Log.d(TAG, "Scan timed out.")
                stopScan()
                listener.onStatusUpdate("掃描超時，找不到裝置")
            }
        }, SCAN_PERIOD)

        isScanning = true
        listener.onStatusUpdate("正在掃描 SmartMedBox...")
        bluetoothAdapter?.bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private fun stopScan() {
        if (isScanning) {
            isScanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "Scan stopped.")
        }
    }


    private fun connectToDevice(device: BluetoothDevice) {
        listener.onStatusUpdate("找到裝置，正在連接...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
    }
    
    private fun writeCommand(command: ByteArray) {
        if (bluetoothGatt == null || commandCharacteristic == null) {
            Log.w(TAG, "GATT not connected or characteristic not found.")
            return
        }
        commandCharacteristic?.value = command
        commandCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        bluetoothGatt?.writeCharacteristic(commandCharacteristic)
    }

    fun setReminder(slotMask: Byte, hour: Int, minute: Int) {
        val command = byteArrayOf(CMD_SET_REMINDER.toByte(), slotMask, hour.toByte(), minute.toByte())
        Log.d(TAG, "Sending SET_REMINDER: Mask=$slotMask, Time=$hour:$minute")
        writeCommand(command)
    }

    fun syncTime() {
        val cal = Calendar.getInstance()
        val year = (cal.get(Calendar.YEAR) - 2000).toByte()
        val month = (cal.get(Calendar.MONTH) + 1).toByte()
        val day = cal.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = cal.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = cal.get(Calendar.MINUTE).toByte()
        val second = cal.get(Calendar.SECOND).toByte()
        val command = byteArrayOf(CMD_SYNC_TIME.toByte(), year, month, day, hour, minute, second)
        Log.d(TAG, "Sending SYNC_TIME")
        writeCommand(command)
    }

    fun cancelAllReminders() {
        val command = byteArrayOf(CMD_CANCEL_ALL_REMINDERS.toByte())
        Log.d(TAG, "Sending CANCEL_ALL_REMINDERS")
        writeCommand(command)
    }


    interface BleListener {
        fun onStatusUpdate(message: String)
        fun onDeviceConnected()
        fun onDeviceDisconnected()
        fun onMedicationTaken(slotNumber: Int)
        fun onBoxStatusUpdate(slotMask: Byte)
    }

    companion object {
        private const val TAG = "BluetoothLeManager"
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds
        
        private const val SERVICE_UUID = "0000a000-0000-1000-8000-00805f9b34fb"
        private const val COMMAND_CHANNEL_UUID = "0000a001-0000-1000-8000-00805f9b34fb"
        private const val DATA_EVENT_CHANNEL_UUID = "0000a002-0000-1000-8000-00805f9b34fb"

        private const val CMD_SET_REMINDER = 0x10
        private const val CMD_SYNC_TIME = 0x11
        private const val CMD_CANCEL_ALL_REMINDERS = 0x12

        private const val EVT_MEDICATION_TAKEN = 0x81
        private const val EVT_TIME_SYNC_ACK = 0x82
        private const val EVT_BOX_STATUS_UPDATE = 0x83
    }
}