// **BluetoothLeManager.kt (V8.5 - Inline suppression)**
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
class BluetoothLeManager(private val context: Context, private val listener: BleListener) {

    interface BleListener {
        fun onStatusUpdate(message: String)
        fun onDeviceConnected()
        fun onDeviceDisconnected()
        fun onMedicationTaken(slotNumber: Int)
        fun onBoxStatusUpdate(slotMask: Byte)
        fun onTimeSyncAcknowledged()
        fun onSensorData(temperature: Float, humidity: Float)
        fun onHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float)
        fun onHistoricDataComplete()
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


    companion object {
        private const val DEVICE_NAME = "ESP32_Medication_Box"
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("c8c7c599-809c-43a5-b825-1038aa349e5d")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    listener.onStatusUpdate("已連接至 $deviceAddress，正在搜尋服務...")
                    handler.post { gatt.discoverServices() }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    this@BluetoothLeManager.gatt?.close()
                    this@BluetoothLeManager.gatt = null
                    isCommandInProgress = false
                    commandQueue.clear()
                    listener.onDeviceDisconnected()
                }
            } else {
                gatt.close()
                this@BluetoothLeManager.gatt = null
                isCommandInProgress = false
                commandQueue.clear()
                listener.onStatusUpdate("連接失敗，狀態碼: $status")
                listener.onDeviceDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    listener.onStatusUpdate("找不到指定的服務 UUID")
                    disconnect()
                    return
                }
                writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
                if (writeCharacteristic == null || notifyCharacteristic == null) {
                    listener.onStatusUpdate("找不到指定的特徵 UUID")
                    disconnect()
                    return
                }
                listener.onStatusUpdate("服務和特徵已找到，正在啟用通知...")
                enableNotifications()
            } else {
                listener.onStatusUpdate("服務搜尋失敗，狀態碼: $status")
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
                listener.onStatusUpdate("通知已成功啟用")
                listener.onDeviceConnected()
            } else {
                listener.onStatusUpdate("啟用通知失敗，狀態碼: $status")
                disconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            handler.postDelayed({
                isCommandInProgress = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    processNextCommand()
                } else {
                    listener.onStatusUpdate("寫入命令失敗")
                    commandQueue.clear()
                }
            }, 150) // 增加延遲以等待 ESP32 處理
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
            when (data[0].toInt() and 0xFF) {
                0x80 -> { if (data.size > 1) listener.onBoxStatusUpdate(data[1]) }
                0x81 -> {
                    if (data.size > 1) listener.onMedicationTaken(data[1].toInt())
                }
                0x82 -> { listener.onTimeSyncAcknowledged() }
                0x90 -> { // Instantaneous sensor data
                    if (data.size > 4) {
                        val temp = data[1] + data[2] / 100f
                        val hum = data[3] + data[4] / 100f
                        listener.onSensorData(temp, hum)
                    }
                }
                0x91 -> { // Historic sensor data point
                    if (data.size > 8) {
                        val buffer = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN)
                        val timestamp = buffer.int.toLong()
                        val temp = data[5] + data[6] / 100f
                        val hum = data[7] + data[8] / 100f
                        listener.onHistoricSensorData(timestamp, temp, hum)
                    }
                }
                0x92 -> { listener.onHistoricDataComplete() } // End of historic data
                0xEE -> { // Error report
                    if (data.size > 1) listener.onError(data[1].toInt())
                }
            }
        }
    }


    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                stopScan()
                listener.onStatusUpdate("找到藥盒，正在連接...")
                connect(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            listener.onStatusUpdate("掃描失敗，錯誤碼: $errorCode")
        }
    }

    fun startScan() {
        if (isScanning) return
        listener.onStatusUpdate("正在掃描智慧藥盒...")
        isScanning = true
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                listener.onStatusUpdate("掃描超時，未找到藥盒")
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
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    fun isConnected(): Boolean {
        return gatt != null
    }
    
    private fun sendCommand(command: ByteArray) {
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
        } else { // 修正後的 else 區塊位置
             isCommandInProgress = false
        }
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

    fun setEngineeringMode(enable: Boolean) {
        val command = byteArrayOf(
            0x13.toByte(),
            if (enable) 0x01.toByte() else 0x00.toByte()
        )
        sendCommand(command)
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
