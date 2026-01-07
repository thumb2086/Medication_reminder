package com.example.medicationreminderapp

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
import com.example.medicationreminderapp.model.CharacterManager
import com.example.medicationreminderapp.util.UpdateManager
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
 class MainActivity : BaseActivity(), BluetoothLeManager.BleListener {

    internal lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var bluetoothLeManager: BluetoothLeManager
    private var alarmManager: AlarmManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var updateManager: UpdateManager
    private lateinit var characterManager: CharacterManager
    private var currentEngineeringModeState: Boolean? = null

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
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        characterManager = CharacterManager(this)
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
        updateManager = UpdateManager(this.applicationContext)

        createNotificationChannel()
        requestAppPermissions()
        observeViewModel()
        setupFragmentNavigation()
        checkForUpdates()
        checkForCharacterUpdates()
    }

    private fun checkForCharacterUpdates() {
        lifecycleScope.launch {
            characterManager.checkForUpdates()
        }
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
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                updateManager.downloadAndInstall(this, updateInfo.downloadUrl, "MedicationReminderApp-${updateInfo.version}.apk")
            }
            .setNegativeButton(getString(R.string.update_later), null)
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
        val characterId = prefs.getString("character", "kuromi") ?: "kuromi"
        val themeName = "Theme_MedicationReminderApp_${characterId.replace("_", "")}"
        val themeResId = resources.getIdentifier(themeName, "style", packageName)
        
        if (themeResId != 0) {
            setTheme(themeResId)
        } else {
            setTheme(R.style.Theme_MedicationReminderApp) // Fallback to default theme
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bleStatus.collect { statusResId ->
                        viewModel.setBleStatus(statusResId)
                    }
                }

                launch {
                    viewModel.isEngineeringMode.collect { isEnabled ->
                        if (currentEngineeringModeState != isEnabled) {
                            currentEngineeringModeState = isEnabled
                            setupViewPagerAndTabs(isEnabled)
                        }
                    }
                }

                 launch {
                    viewModel.isReconnecting.collect { _ ->
                        // You can add UI changes here based on the reconnecting state if needed
                        // For example, showing/hiding a progress bar
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

    private fun setupViewPagerAndTabs(isEngineeringMode: Boolean) {
        val currentTabPosition = binding.tabLayout.selectedTabPosition.takeIf { it != -1 } ?: 0

        binding.viewPager.adapter = ViewPagerAdapter(this, isEngineeringMode)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_reminders)
                1 -> getString(R.string.tab_medication_list)
                2 -> getString(R.string.tab_log)
                3 -> getString(R.string.tab_report)
                4 -> getString(R.string.tab_environment)
                else -> null
            }
        }.attach()

        if (currentTabPosition < binding.tabLayout.tabCount) {
            binding.viewPager.setCurrentItem(currentTabPosition, false)
        }
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

    override fun onStatusUpdate(@StringRes messageResId: Int, vararg formatArgs: Any) {
        viewModel.setBleStatus(messageResId)
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

    override fun onReconnectStarted() {
        viewModel.onReconnectStarted()
    }

    override fun onReconnectFailed() {
        viewModel.onReconnectFailed()
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

    override fun onOtaProgressUpdate(progress: Int) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is FirmwareUpdateFragment) {
            currentFragment.onOtaProgressUpdate(progress)
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            setSound(soundUri, audioAttributes)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "medication_reminder_channel"
    }
}
