// **BluetoothLeManager.kt (V8.13 - Add Alarm Support & Error Handling)**
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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class BluetoothLeManager @Inject constructor(@ApplicationContext private val context: Context) {

    var listener: BleListener? = null

    interface BleListener {
        fun onStatusUpdate(@StringRes messageResId: Int, vararg formatArgs: Any)
        fun onDeviceConnected()
        fun onDeviceDisconnected()
        fun onReconnectStarted()
        fun onReconnectFailed()
        fun onProtocolVersionReported(version: Int)
        fun onMedicationTaken(slotNumber: Int)
        fun onBoxStatusUpdate(slotMask: Byte)
        fun onTimeSyncAcknowledged()
        fun onEngineeringModeUpdate(isEngineeringMode: Boolean)
        fun onSensorData(temperature: Float, humidity: Float)
        fun onHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float)
        fun onHistoricDataComplete()
        fun onWifiStatusUpdate(status: Int)
        fun onError(errorCode: Int)
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val commandQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isCommandInProgress = false
    private var protocolVersion: Int = 1

    private var shouldAutoReconnect = false
    private var lastConnectedDevice: BluetoothDevice? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMillis = 5000L

    companion object {
        private const val TAG = "BluetoothLeManager"
        private const val DEVICE_NAME = "SmartMedBox"
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("c8c7c599-809c-43a5-b825-1038aa349e5d")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Connected to $deviceAddress")
                stopReconnectSequence()
                lastConnectedDevice = gatt.device
                handler.post { gatt.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from $deviceAddress with status: $status")
                val wasConnected = this@BluetoothLeManager.gatt != null
                cleanupConnection()
                handler.post { listener?.onDeviceDisconnected() }

                if (wasConnected && shouldAutoReconnect) {
                    Log.i(TAG, "Unexpected disconnect detected. Starting reconnect sequence.")
                    startReconnectSequence()
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection state change error: $status")
                cleanupConnection()
                handler.post { listener?.onStatusUpdate(R.string.ble_status_connect_error, status) }
                handler.post { listener?.onDeviceDisconnected() }

                if (shouldAutoReconnect) {
                    Log.i(TAG, "Connection error. Starting reconnect sequence.")
                    startReconnectSequence()
                }
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Service not found: $SERVICE_UUID")
                    handler.post { listener?.onStatusUpdate(R.string.ble_status_service_not_found) }
                    disconnect()
                    return
                }
                writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
                if (writeCharacteristic == null || notifyCharacteristic == null) {
                    Log.e(TAG, "Characteristics not found")
                    handler.post { listener?.onStatusUpdate(R.string.ble_status_char_not_found) }
                    disconnect()
                    return
                }
                handler.post { listener?.onStatusUpdate(R.string.ble_status_enabling_notifications) }
                enableNotifications()
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                handler.post { listener?.onStatusUpdate(R.string.ble_status_service_discovery_failed, status) }
                disconnect()
            }
        }

        private fun enableNotifications() {
            gatt?.let { g ->
                notifyCharacteristic?.let { char ->
                    g.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(CCCD_UUID)
                    descriptor?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            run {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                g.writeDescriptor(it)
                            }
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notifications enabled. Connection fully established.")
                reconnectAttempts = 0
                handler.post {
                    listener?.onStatusUpdate(R.string.ble_status_notifications_enabled)
                    listener?.onDeviceConnected()
                }
                requestProtocolVersion()
                enableRealtimeSensorData()
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
                handler.post { listener?.onStatusUpdate(R.string.ble_status_notification_enable_failed, status) }
                disconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            handler.postDelayed({
                isCommandInProgress = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    processNextCommand()
                } else {
                    Log.e(TAG, "Characteristic write failed: $status")
                    handler.post { listener?.onStatusUpdate(R.string.ble_status_write_failed) }
                    commandQueue.clear()
                }
            }, 150)
        }

        @Deprecated("Used for Android versions prior to 13 (TIRAMISU)")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleIncomingData(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleIncomingData(value)
        }

        private fun handleIncomingData(data: ByteArray) {
            if (data.isEmpty()) return
            val hexString = data.joinToString("") { "%02x".format(it) }
            Log.d(TAG, "RX: $hexString")

            when (data[0].toInt() and 0xFF) {
                0x71 -> {
                    if (data.size > 1) {
                        protocolVersion = data[1].toInt()
                        Log.d(TAG, "Protocol Version: $protocolVersion")
                        handler.post { listener?.onProtocolVersionReported(protocolVersion) }
                    }
                }
                0x80 -> { if (data.size > 1) handler.post { listener?.onBoxStatusUpdate(data[1]) } }
                0x81 -> { if (data.size > 1) handler.post { listener?.onMedicationTaken(data[1].toInt()) } }
                0x82 -> { handler.post { listener?.onTimeSyncAcknowledged() } }
                0x83 -> {
                    if (data.size > 1) handler.post { listener?.onEngineeringModeUpdate(data[1].toInt() == 0x01) }
                }
                0x84 -> {
                    if (data.size > 1) {
                        val wifiStatus = data[1].toInt()
                        Log.d(TAG, "Wi-Fi Status Update: $wifiStatus")
                        handler.post { listener?.onWifiStatusUpdate(wifiStatus) }
                    }
                }
                0x90 -> {
                    if (data.size >= 5) {
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val temperature = buffer.getShort(1) / 100.0f
                        val humidity = buffer.getShort(3) / 100.0f
                        Log.d(TAG, "Realtime Sensor (V2): T=$temperature, H=$humidity")
                        handler.post { listener?.onSensorData(temperature, humidity) }
                    }
                }
                0x91 -> {
                    if (protocolVersion >= 2) {
                        if (data.size < 9 || (data.size - 1) % 8 != 0) {
                            Log.w(TAG, "Invalid V2 historic data size: ${data.size}")
                            return
                        }
                        val numRecords = (data.size - 1) / 8
                        if (numRecords > 5) return

                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until numRecords) {
                            val offset = 1 + i * 8
                            val timestamp = buffer.getInt(offset).toLong()
                            val temp = buffer.getShort(offset + 4) / 100.0f
                            val hum = buffer.getShort(offset + 6) / 100.0f
                            Log.d(TAG, "Historic(V2) [$i]: TS=$timestamp, T=$temp, H=$hum")
                            handler.post { listener?.onHistoricSensorData(timestamp, temp, hum) }
                        }
                    } else {
                        if (data.size < 9) {
                             Log.w(TAG, "Invalid V1 historic data size: ${data.size}")
                             return
                        }
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val timestamp = buffer.getInt(1).toLong()
                        val temp = buffer.getShort(5) / 100.0f
                        val hum = buffer.getShort(7) / 100.0f
                        Log.d(TAG, "Historic(V1): TS=$timestamp, T=$temp, H=$hum")
                        handler.post { listener?.onHistoricSensorData(timestamp, temp, hum) }
                    }
                }
                0x92 -> {
                    Log.d(TAG, "Historic data sync complete")
                    handler.post { listener?.onHistoricDataComplete() }
                }
                0xEE -> {
                    if (data.size > 1) {
                        val errorCode = data[1].toInt()
                        val errorMsg = when(errorCode) {
                            0x02 -> "感測器錯誤"
                            0x03 -> "未知指令 (0x03)"
                            0x04 -> "存取錯誤 (0x04)"
                            0x05 -> "長度錯誤 (0x05)"
                            else -> "未知錯誤: $errorCode"
                        }
                        Log.e(TAG, "Device Error: $errorMsg")
                        handler.post {
                            listener?.onStatusUpdate(R.string.ble_status_device_error, errorMsg)
                            listener?.onError(errorCode)
                        }
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                stopScan()
                Log.d(TAG, "Found device: ${result.device.address}")
                handler.post { listener?.onStatusUpdate(R.string.ble_status_found_connecting) }
                connect(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e(TAG, "Scan failed: $errorCode")
            handler.post { listener?.onStatusUpdate(R.string.ble_status_scan_failed, errorCode) }
        }
    }

    fun startScan() {
        if (isScanning) return
        handler.post { listener?.onStatusUpdate(R.string.ble_status_scanning) }
        isScanning = true
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                handler.post { listener?.onStatusUpdate(R.string.ble_status_scan_timeout) }
            }
        }, 10000)
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
    }

    private fun stopScan() {
        if (isScanning && bluetoothAdapter?.isEnabled == true) {
            isScanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    private fun connect(device: BluetoothDevice) {
        shouldAutoReconnect = true
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        shouldAutoReconnect = false
        stopReconnectSequence()
        gatt?.disconnect()
    }

    fun isConnected(): Boolean {
        return gatt != null
    }

    private fun sendCommand(command: ByteArray) {
        val hexString = command.joinToString("") { "%02x".format(it) }
        Log.d(TAG, "TX: $hexString")
        commandQueue.add(command)
        if (!isCommandInProgress) {
            processNextCommand()
        }
    }

    private fun processNextCommand() {
        if (commandQueue.isEmpty() || gatt == null || writeCharacteristic == null) {
            isCommandInProgress = false
            return
        }
        val command = commandQueue.poll()
        if (command != null) {
            isCommandInProgress = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeCharacteristic(writeCharacteristic!!, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                run {
                    writeCharacteristic?.value = command
                    writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    gatt?.writeCharacteristic(writeCharacteristic!!)
                }
            }
        } else {
             isCommandInProgress = false
        }
    }

    private fun startReconnectSequence() {
        if (reconnectAttempts == 0) {
            handler.post { listener?.onReconnectStarted() }
        }

        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${reconnectDelayMillis}ms")
            reconnectHandler.postDelayed({
                lastConnectedDevice?.let { device ->
                    Log.i(TAG, "Attempting to reconnect to ${device.address} (Attempt $reconnectAttempts/$maxReconnectAttempts)")
                    gatt = device.connectGatt(context, false, gattCallback)
                } ?: run {
                    Log.e(TAG, "Cannot reconnect, no last connected device.")
                    handler.post { listener?.onReconnectFailed() }
                }
            }, reconnectDelayMillis)
        } else {
            Log.e(TAG, "Reconnect failed after $maxReconnectAttempts attempts.")
            reconnectAttempts = 0
            handler.post { listener?.onReconnectFailed() }
        }
    }

    private fun stopReconnectSequence() {
        Log.d(TAG, "Stopping reconnect sequence.")
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempts = 0
    }

    private fun cleanupConnection() {
        gatt?.close()
        gatt = null
        isCommandInProgress = false
        commandQueue.clear()
        protocolVersion = 1
    }

    fun requestProtocolVersion() {
        sendCommand(byteArrayOf(0x01.toByte()))
    }

    fun enableRealtimeSensorData() {
        sendCommand(byteArrayOf(0x32.toByte()))
        Log.d(TAG, "TX: Enable Realtime ENV")
    }

    fun syncTime() {
        val now = java.util.Calendar.getInstance()
        val command = byteArrayOf(
            0x11.toByte(),
            (now.get(java.util.Calendar.YEAR) - 2000).toByte(),
            (now.get(java.util.Calendar.MONTH) + 1).toByte(),
            now.get(java.util.Calendar.DAY_OF_MONTH).toByte(),
            now.get(java.util.Calendar.HOUR_OF_DAY).toByte(),
            now.get(java.util.Calendar.MINUTE).toByte(),
            now.get(java.util.Calendar.SECOND).toByte()
        )
        sendCommand(command)
    }

    fun sendWifiCredentials(ssid: String, pass: String) {
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        val passBytes = pass.toByteArray(Charsets.UTF_8)
        val command = ByteArray(3 + ssidBytes.size + passBytes.size)
        command[0] = 0x12.toByte()
        command[1] = ssidBytes.size.toByte()
        System.arraycopy(ssidBytes, 0, command, 2, ssidBytes.size)
        command[2 + ssidBytes.size] = passBytes.size.toByte()
        System.arraycopy(passBytes, 0, command, 3 + ssidBytes.size, passBytes.size)
        sendCommand(command)
    }

    fun setAlarm(slot: Int, hour: Int, minute: Int, enable: Boolean) {
        val command = byteArrayOf(
            0x41.toByte(),
            slot.toByte(),
            hour.toByte(),
            minute.toByte(),
            if (enable) 1.toByte() else 0.toByte()
        )
        sendCommand(command)
        Log.d(TAG, "TX: Set Alarm Slot $slot to $hour:$minute, Enable: $enable")
    }

    fun guidePillbox(slot: Int) {
        val command = byteArrayOf(0x42.toByte(), slot.toByte())
        sendCommand(command)
        Log.d(TAG, "TX: Guide to slot #$slot")
    }

    @Suppress("unused")
    fun setEngineeringMode(enable: Boolean) {
        val command = byteArrayOf(0x13.toByte(), if (enable) 0x01.toByte() else 0x00.toByte())
        sendCommand(command)
        Toast.makeText(context, "工程模式狀態已發送", Toast.LENGTH_SHORT).show()
    }

    fun requestEngineeringModeStatus() {
        sendCommand(byteArrayOf(0x14.toByte()))
    }



    fun requestStatus() {
        sendCommand(byteArrayOf(0x20.toByte()))
    }

    fun requestEnvironmentData() {
        sendCommand(byteArrayOf(0x30.toByte()))
    }

    fun requestHistoricEnvironmentData() {
        sendCommand(byteArrayOf(0x31.toByte()))
    }
}
