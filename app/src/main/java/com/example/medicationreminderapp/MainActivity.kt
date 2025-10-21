package com.example.medicationreminderapp

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.databinding.ActivityMainBinding
import com.example.medicationreminderapp.ui.MainViewModel
import com.example.medicationreminderapp.ui.ViewPagerAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), BluetoothLeManager.BleListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
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
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        gson = Gson()
        alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
        bluetoothLeManager = BluetoothLeManager(this, this)

        loadAllData()
        createNotificationChannel()
        setupViewPager()
        setupListeners()
        requestAppPermissions()
    }

    private fun setupViewPager() {
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

    override fun onResume() {
        super.onResume()
        loadDailyStatusData()
        viewModel.updateComplianceRate(viewModel.medicationList.value ?: listOf(), viewModel.dailyStatusMap.value ?: mapOf())
    }

    override fun onPause() {
        super.onPause()
        saveAllData()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeManager.disconnect()
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener { showThemeDialog() }
    }

    private fun requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager?.canScheduleExactAlarms() == false) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(getString(R.string.themes_light), getString(R.string.themes_dark), getString(R.string.themes_default))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_theme_title))
            .setItems(themes) { _, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            .show()
    }

    fun requestBluetoothPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else { arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) }

        val permissionsToRequestList = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        val permissionsToRequest = permissionsToRequestList.toTypedArray()

        if (permissionsToRequest.isEmpty()) { bluetoothLeManager.startScan() }
        else { multiplePermissionsLauncher.launch(permissionsToRequest) }
    }

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
            val medication = viewModel.medicationList.value?.find { it.slotNumber == slotNumber }
            medication?.let {
                if (it.remainingPills > 0) { it.remainingPills-- }
                saveMedicationData()
                checkLowStock(it)
            }
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val newStatusMap = viewModel.dailyStatusMap.value?.toMutableMap() ?: mutableMapOf()
            newStatusMap[todayStr] = STATUS_ALL_TAKEN
            viewModel.dailyStatusMap.value = newStatusMap
            saveDailyStatusData()
            viewModel.updateComplianceRate(viewModel.medicationList.value ?: listOf(), newStatusMap)
        }
    }

    override fun onTimeSyncAcknowledged() {
        runOnUiThread {
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val message = getString(R.string.time_sync_success, currentTime)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            syncRemindersToBox()
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
        if (slotNumber in 1..8) { // Repurposing this callback for guided fill confirmation
             runOnUiThread {
                Log.d("MainActivity", "Slot $slotNumber filled confirmation received via onBoxStatusUpdate.")
                viewModel.onGuidedFillConfirmed()
            }
        }
    }

    fun addMedication(medication: Medication) {
        val list = viewModel.medicationList.value?.toMutableList() ?: mutableListOf()
        list.add(medication)
        viewModel.medicationList.value = list
        saveAllData()
        setAlarmForMedication(medication)
        Toast.makeText(this, getString(R.string.medication_added_to_slot, medication.name, medication.slotNumber), Toast.LENGTH_SHORT).show()
    }

    private fun checkLowStock(medication: Medication) {
        val threshold = (medication.totalPills * 0.1).toInt().coerceAtLeast(1)
        if (medication.remainingPills > 0 && medication.remainingPills <= threshold) {
            AlertDialog.Builder(this)
                .setTitle(R.string.low_stock_warning_title)
                .setMessage(getString(R.string.low_stock_warning_message, medication.name, medication.remainingPills))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    fun rotateToSlot(slotNumber: Int) {
        if (viewModel.isBleConnected.value != true) {
            Toast.makeText(this, getString(R.string.connect_box_first), Toast.LENGTH_SHORT).show()
            return
        }
        val command = "{\"action\":\"rotate_to_slot\",\"payload\":{\"slot\":$slotNumber}}"
        // bluetoothLeManager.sendCommand(command) // TODO: Implement sendCommand in BluetoothLeManager
        Log.d("MainActivity", "Sending command: $command")
    }

    fun syncRemindersToBox() {
        if (viewModel.isBleConnected.value != true) return

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val remindersJson = viewModel.medicationList.value?.mapNotNull { med ->
            if (med.times.isEmpty()) return@mapNotNull null
            val timesArray = med.times.values.joinToString(",") { timeMillis ->
                "\"${timeFormat.format(Date(timeMillis))}\"".trim()
            }
            "{\"slot\":${med.slotNumber},\"times\":[$timesArray]}"
        }?.joinToString(",")

        val remindersPayload = remindersJson ?: ""
        val syncTime = System.currentTimeMillis() / 1000
        val command = "{\"action\":\"sync_reminders\",\"payload\":{\"sync_time\":$syncTime,\"reminders\":[$remindersPayload]}}"
        
        // bluetoothLeManager.sendCommand(command) // TODO: Implement in BluetoothLeManager
        Log.d("MainActivity", "Sending command: $command")
        runOnUiThread { Toast.makeText(this, getString(R.string.reminders_synced_to_box), Toast.LENGTH_SHORT).show() }
    }

    private fun setAlarmForMedication(medication: Medication) {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        medication.times.forEach { (timeType, timeMillis) ->
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("medicationName", medication.name)
                putExtra("dosage", medication.dosage)
                putExtra("medicationId", medication.id + timeType)
                putExtra("medicationEndDate", medication.endDate)
                putExtra("originalAlarmTimeOfDay", timeMillis)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, flags)

            val alarmTime = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            var nextAlarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarmTime[Calendar.HOUR_OF_DAY])
                set(Calendar.MINUTE, alarmTime[Calendar.MINUTE])
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            val startCalendar = Calendar.getInstance().apply { timeInMillis = medication.startDate }
            if (nextAlarmTime.before(startCalendar)) {
                nextAlarmTime = startCalendar.apply {
                    set(Calendar.HOUR_OF_DAY, alarmTime[Calendar.HOUR_OF_DAY])
                    set(Calendar.MINUTE, alarmTime[Calendar.MINUTE])
                }
                 if (nextAlarmTime.before(Calendar.getInstance())) { nextAlarmTime.add(Calendar.DATE, 1) }
            }
            val endCalendar = Calendar.getInstance().apply { timeInMillis = medication.endDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
            if (nextAlarmTime.before(endCalendar)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime.timeInMillis, pendingIntent)
                        } else {
                            am.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime.timeInMillis, pendingIntent)
                        }
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime.timeInMillis, pendingIntent)
                    }
                    Log.d("AlarmScheduler", "為 ${medication.name} 設定鬧鐘在 ${Date(nextAlarmTime.timeInMillis)}")
                } catch (e: SecurityException) {
                    Log.e("AlarmScheduler", "無法設定精確鬧鐘，請檢查權限", e)
                }
            }
        }
        if (viewModel.isBleConnected.value == true) { syncRemindersToBox() }
    }

    fun deleteMedication(medication: Medication, showToast: Boolean = true) {
        medication.times.forEach { (timeType, _) ->
            val intent = Intent(this, AlarmReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, flags)

            if (pIntent != null) alarmManager?.cancel(pIntent)
        }
        val list = viewModel.medicationList.value?.toMutableList() ?: mutableListOf()
        list.remove(medication)
        viewModel.medicationList.value = list

        val notes = viewModel.notesMap.value?.toMutableMap() ?: mutableMapOf()
        notes.remove(medication.name)
        viewModel.notesMap.value = notes

        if (viewModel.isBleConnected.value == true) {
            bluetoothLeManager.cancelReminder(medication.slotNumber)
        }
        saveAllData()
        if (showToast) {
            Toast.makeText(this, getString(R.string.medication_deleted, medication.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val ch = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.notification_channel_description); enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun loadAllData() { loadMedicationData(); loadNotesData(); loadDailyStatusData() }
    private fun saveAllData() { saveMedicationData(); saveNotesData(); saveDailyStatusData() }

    private fun saveMedicationData() {
        sharedPreferences.edit {
            putString(KEY_MEDICATION_DATA, gson.toJson(viewModel.medicationList.value))
        }
    }
    private fun loadMedicationData() {
        sharedPreferences.getString(KEY_MEDICATION_DATA, null)?.let {
            try {
                val data: MutableList<Medication> = gson.fromJson(it, object : TypeToken<MutableList<Medication>>() {}.type) ?: mutableListOf()
                viewModel.medicationList.value = data
            } catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse med data failed", e) }
        }
    }

    private fun saveNotesData() {
        sharedPreferences.edit {
            putString(KEY_NOTES_DATA, gson.toJson(viewModel.notesMap.value))
        }
    }
    private fun loadNotesData() {
        sharedPreferences.getString(KEY_NOTES_DATA, null)?.let {
            try {
                val data: MutableMap<String, String> = gson.fromJson(it, object : TypeToken<MutableMap<String, String>>() {}.type) ?: mutableMapOf()
                viewModel.notesMap.value = data
            } catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse notes data failed", e) }
        }
    }

    private fun saveDailyStatusData() {
        sharedPreferences.edit {
            putString(KEY_DAILY_STATUS, gson.toJson(viewModel.dailyStatusMap.value))
        }
    }
    private fun loadDailyStatusData() {
        sharedPreferences.getString(KEY_DAILY_STATUS, null)?.let {
            try {
                val data: MutableMap<String, Int> = gson.fromJson(it, object : TypeToken<MutableMap<String, Int>>() {}.type) ?: mutableMapOf()
                viewModel.dailyStatusMap.value = data
            } catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse daily status failed", e) }
        }
    }

    companion object {
        const val CHANNEL_ID = "medication_reminder_channel"
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_NOTES_DATA = "notes_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
        fun generateNotificationId(): Int = Random().nextInt(1_000_000)
    }
}
