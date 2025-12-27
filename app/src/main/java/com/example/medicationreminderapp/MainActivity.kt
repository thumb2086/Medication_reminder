package com.example.medicationreminderapp

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.medicationreminderapp.adapter.ViewPagerAdapter
import com.example.medicationreminderapp.databinding.ActivityMainBinding
import com.example.medicationreminderapp.util.UpdateManager
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BluetoothLeManager.BleListener {

    internal lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var bluetoothLeManager: BluetoothLeManager
    private var alarmManager: AlarmManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var updateManager: UpdateManager

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
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        applyFontSize()
        applyCharacterTheme()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = systemBars.top)
            insets
        }

        alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
        bluetoothLeManager.listener = this
        updateManager = UpdateManager(this)

        createNotificationChannel()
        setupViewPagerAndTabs()
        requestAppPermissions()
        observeViewModel()
        setupFragmentNavigation()
        checkForUpdates()
    }
    private fun applyFontSize() {
        val fontSize = prefs.getString("font_size", "medium")
        val themeResId = when (fontSize) {
            "small" -> R.style.Theme_MedicationReminderApp_SmallText
            "medium" -> R.style.Theme_MedicationReminderApp_MediumText
            "large" -> R.style.Theme_MedicationReminderApp_LargeText
            else -> R.style.Theme_MedicationReminderApp_MediumText
        }
        setTheme(themeResId)
    }

    private fun checkForUpdates() {
        // Automatically checks based on the channel defined in BuildConfig
        // Pass isManualCheck = false for auto check at startup
        lifecycleScope.launch {
            val updateInfo = updateManager.checkForUpdates(isManualCheck = false)
            if (updateInfo != null) {
                showUpdateDialog(updateInfo)
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateManager.UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, updateInfo.version, updateInfo.releaseNotes))
            .setPositiveButton(R.string.update_now) { _, _ ->
                updateManager.downloadAndInstall(updateInfo.downloadUrl, "MedicationReminderApp-${updateInfo.version}.apk")
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun setupFragmentNavigation() {
        supportFragmentManager.addOnBackStackChangedListener {
            val isFragmentOnBackStack = supportFragmentManager.backStackEntryCount > 0
            updateUiForFragment(isFragmentOnBackStack)
        }
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUiForFragment(false)
            }
        })
        // Postpone UI update to ensure fragment manager has restored its state
        Handler(Looper.getMainLooper()).post {
            updateUiForFragment(supportFragmentManager.backStackEntryCount > 0)
        }
    }

    fun updateUiForFragment(isFragmentOnBackStack: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isFragmentOnBackStack)
        binding.tabLayout.visibility = if (isFragmentOnBackStack) View.GONE else View.VISIBLE
        binding.viewPager.visibility = if (isFragmentOnBackStack) View.GONE else View.VISIBLE
        invalidateOptionsMenu()
    }

    private fun applyCharacterTheme() {
        val character = prefs.getString("character", "kuromi")
        val themeResId = when (character) {
            "kuromi" -> R.style.Theme_MedicationReminderApp_Kuromi
            "maruko" -> R.style.Theme_MedicationReminderApp_MyMelody
            else -> R.style.Theme_MedicationReminderApp
        }
        setTheme(themeResId)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bleStatus.collect { status ->
                        viewModel.setBleStatus(status)
                    }
                }

                viewModel.requestBleAction.observe(this@MainActivity) { action ->
                    if (viewModel.isBleConnected.value) {
                        when (action) {
                            MainViewModel.BleAction.REQUEST_ENV_DATA -> bluetoothLeManager.requestEnvironmentData()
                            MainViewModel.BleAction.REQUEST_HISTORIC_ENV_DATA -> bluetoothLeManager.requestHistoricEnvironmentData()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, R.string.connect_box_first, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_settings)?.isVisible = supportFragmentManager.backStackEntryCount == 0
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_reminders)
                1 -> getString(R.string.tab_medication_list)
                2 -> getString(R.string.tab_log)
                3 -> getString(R.string.tab_environment)
                else -> null
            }
        }.attach()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_settings -> {
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, SettingsFragment())
                    addToBackStack(null)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeManager.disconnect()
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

    @Suppress("unused")
    fun setLocale(languageCode: String?) {
        val locales = if (languageCode == "system" || languageCode.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    // --- BluetoothLeManager.BleListener Callbacks ---

    override fun onStatusUpdate(message: String) {
        viewModel.setBleStatus(message)
    }

    override fun onDeviceConnected() {
        viewModel.setBleConnectionState(true)
        runOnUiThread {
            Handler(Looper.getMainLooper()).postDelayed({ bluetoothLeManager.requestStatus() }, 500)
            Handler(Looper.getMainLooper()).postDelayed({ bluetoothLeManager.syncTime() }, 1000)
            Handler(Looper.getMainLooper()).postDelayed({ bluetoothLeManager.requestEngineeringModeStatus() }, 1500)
        }
    }

    override fun onDeviceDisconnected() {
         viewModel.setBleConnectionState(false)
    }
    
    override fun onProtocolVersionReported(version: Int) {
        runOnUiThread {
            Toast.makeText(this, "藥盒協定版本: $version", Toast.LENGTH_LONG).show()
        }
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
    
    override fun onEngineeringModeUpdate(isEngineeringMode: Boolean) {
        viewModel.setEngineeringMode(isEngineeringMode)
        runOnUiThread {
            prefs.edit { putBoolean("engineering_mode", isEngineeringMode) }
            val status = if (isEngineeringMode) "啟用" else "關閉"
            Toast.makeText(this, "藥盒回報：工程模式已 $status", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSensorData(temperature: Float, humidity: Float) {
        viewModel.onNewSensorData(temperature, humidity)
    }

    override fun onHistoricSensorData(timestamp: Long, temperature: Float, humidity: Float) {
        viewModel.addHistoricSensorData(timestamp, temperature, humidity)
    }

    override fun onHistoricDataComplete() {
        viewModel.onHistoricDataSyncCompleted()
    }
    
    override fun onWifiStatusUpdate(status: Int) {
        runOnUiThread {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is WiFiConfigFragment) {
                currentFragment.onWifiStatusUpdate(status)
            }
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
