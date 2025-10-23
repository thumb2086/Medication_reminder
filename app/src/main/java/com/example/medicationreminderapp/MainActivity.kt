package com.example.medicationreminderapp

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.adapter.ViewPagerAdapter
import com.example.medicationreminderapp.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), BluetoothLeManager.BleListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private var alarmManager: AlarmManager? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) { Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show() }
    }
    private val multiplePermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            bluetoothLeManager.startScan()
        } else {
            Toast.makeText(this, getString(R.string.bt_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // ViewModel is shared between Activity and Fragments
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
        bluetoothLeManager = BluetoothLeManager(this, this)

        createNotificationChannel()
        setupViewPagerAndTabs()
        requestAppPermissions()
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_reminders)
                1 -> getString(R.string.tab_log)
                2 -> getString(R.string.tab_environment)
                else -> null
            }
        }.attach()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeManager.disconnect()
    }

    private fun requestAppPermissions() {
        // Request Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request Exact Alarm Permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager?.canScheduleExactAlarms() == false) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    fun requestBluetoothPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsToRequest.isEmpty()) {
            bluetoothLeManager.startScan()
        } else {
            multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // --- BluetoothLeManager.BleListener Callbacks ---

    override fun onStatusUpdate(message: String) {
        runOnUiThread { viewModel.bleStatus.value = message }
    }

    override fun onDeviceConnected() {
        viewModel.isBleConnected.value = true
        runOnUiThread {
            Handler(Looper.getMainLooper()).postDelayed({ bluetoothLeManager.requestStatus() }, 500)
            Handler(Looper.getMainLooper()).postDelayed({ bluetoothLeManager.syncTime() }, 1000)
        }
    }

    override fun onDeviceDisconnected() {
        viewModel.isBleConnected.value = false
    }

    override fun onMedicationTaken(slotNumber: Int) {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.medication_taken_report, slotNumber), Toast.LENGTH_LONG).show()
            viewModel.processMedicationTaken(slotNumber)
        }
    }

    override fun onTimeSyncAcknowledged() {
        runOnUiThread {
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val message = getString(R.string.time_sync_success, currentTime)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSensorData(temperature: Float, humidity: Float) {
        runOnUiThread {
            viewModel.temperature.value = temperature
            viewModel.humidity.value = humidity
        }
    }

    override fun onError(errorCode: Int) {
        runOnUiThread {
            val message = when(errorCode) {
                1 -> getString(R.string.error_jammed)
                2 -> getString(R.string.error_sensor)
                else -> getString(R.string.error_unknown, errorCode)
            }
            AlertDialog.Builder(this).setTitle(getString(R.string.box_anomaly_title)).setMessage(message).setPositiveButton(R.string.ok, null).show()
        }
    }

    override fun onBoxStatusUpdate(slotMask: Byte) {
         val slotNumber = slotMask.toInt()
        if (slotNumber in 1..8) { // Confirmation for guided fill
             runOnUiThread {
                Log.d("MainActivity", "Slot $slotNumber filled confirmation received.")
                viewModel.onGuidedFillConfirmed()
            }
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            setSound(soundUri, audioAttributes)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "medication_reminder_channel"
    }
}
