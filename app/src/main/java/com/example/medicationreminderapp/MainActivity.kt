// **MainActivity.kt (V8 完整版，無省略)**
package com.example.medicationreminderapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.slider.Slider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener, BluetoothLeManager.BleListener {

    // --- UI 元件宣告 ---
    private lateinit var connectBoxButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var addMedicationButton: Button
    private lateinit var showAllMedicationsButton: Button
    private lateinit var bleStatusTextView: TextView
    private lateinit var testCommandButton: Button
    private lateinit var medicationNameEditText: EditText
    private lateinit var dosageSlider: Slider
    private lateinit var dosageValueTextView: TextView
    private lateinit var frequencySpinner: Spinner
    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button
    private lateinit var timeSettingsLayout: LinearLayout
    private lateinit var morningTimeButton: Button
    private lateinit var noonTimeButton: Button
    private lateinit var eveningTimeButton: Button
    private lateinit var bedtimeTimeButton: Button
    private lateinit var morningTimeDisplay: TextView
    private lateinit var noonTimeDisplay: TextView
    private lateinit var eveningTimeDisplay: TextView
    private lateinit var bedtimeTimeDisplay: TextView
    private lateinit var notesEditText: EditText
    private lateinit var displayNotesTextView: TextView
    private lateinit var calendarView: com.kizitonwose.calendar.view.CalendarView
    private lateinit var complianceRateTextView: TextView // 依從率顯示
    private lateinit var complianceProgressBar: ProgressBar // 依從率進度條
    private lateinit var tempTextView: TextView // 溫度顯示
    private lateinit var humidityTextView: TextView // 濕度顯示

    // --- 數據 & 邏輯相關屬性 ---
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private var alarmManager: AlarmManager? = null
    private var medicationList: MutableList<Medication> = mutableListOf()
    private var notesMap: MutableMap<String, String> = mutableMapOf()
    private var dailyStatusMap: MutableMap<String, Int> = mutableMapOf()
    private var isBleConnected = false

    private var startDate: Calendar? = null
    private var endDate: Calendar? = null
    private val selectedTimes = mutableMapOf<Int, Calendar>()

    // --- 權限請求啟動器 ---
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
        applySelectedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeUI()
        initializeDataAndLoad()
        createNotificationChannel()
        setupListeners()
        setupCalendar()
        requestAppPermissions()
        updateComplianceRate() // 首次啟動時計算一次依從率
    }

    override fun onResume() {
        super.onResume()
        loadDailyStatusData()
        calendarView.notifyCalendarChanged()
    }

    override fun onPause() {
        super.onPause()
        saveAllData()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeManager.disconnect()
    }

    private fun initializeUI() {
        connectBoxButton = findViewById(R.id.connectBoxButton)
        settingsButton = findViewById(R.id.settingsButton)
        addMedicationButton = findViewById(R.id.addMedicationButton)
        showAllMedicationsButton = findViewById(R.id.showAllMedicationsButton)
        bleStatusTextView = findViewById(R.id.bleStatusTextView)
        testCommandButton = findViewById(R.id.testCommandButton)
        medicationNameEditText = findViewById(R.id.medicationNameEditText)
        dosageSlider = findViewById(R.id.dosageSlider)
        dosageValueTextView = findViewById(R.id.dosageValueTextView)
        frequencySpinner = findViewById(R.id.frequencySpinner)
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)
        timeSettingsLayout = findViewById(R.id.timeSettingsLayout)
        morningTimeButton = findViewById(R.id.morningTimeButton)
        noonTimeButton = findViewById(R.id.noonTimeButton)
        eveningTimeButton = findViewById(R.id.eveningTimeButton)
        bedtimeTimeButton = findViewById(R.id.bedtimeTimeButton)
        morningTimeDisplay = findViewById(R.id.morningTimeDisplay)
        noonTimeDisplay = findViewById(R.id.noonTimeDisplay)
        eveningTimeDisplay = findViewById(R.id.eveningTimeDisplay)
        bedtimeTimeDisplay = findViewById(R.id.bedtimeTimeDisplay)
        notesEditText = findViewById(R.id.notesEditText)
        displayNotesTextView = findViewById(R.id.displayNotesTextView)
        calendarView = findViewById(R.id.calendarView)
        complianceRateTextView = findViewById(R.id.complianceRateTextView)
        complianceProgressBar = findViewById(R.id.complianceProgressBar)
        tempTextView = findViewById(R.id.tempTextView)
        humidityTextView = findViewById(R.id.humidityTextView)
    }

    private fun initializeDataAndLoad() {
        gson = Gson()
        alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
        bluetoothLeManager = BluetoothLeManager(this, this)
        loadAllData()
    }

    @SuppressLint("DefaultLocale")
    private fun setupListeners() {
        ArrayAdapter.createFromResource(this, R.array.medication_frequency_options, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            frequencySpinner.adapter = it
        }
        frequencySpinner.onItemSelectedListener = this
        medicationNameEditText.addTextChangedListener { notesEditText.setText(notesMap[it.toString()] ?: "") }
        dosageSlider.addOnChangeListener { _, value, _ -> dosageValueTextView.text = String.format(Locale.getDefault(), getString(R.string.dosage_format), value) }
        dosageValueTextView.text = String.format(Locale.getDefault(), getString(R.string.dosage_format), dosageSlider.value)

        settingsButton.setOnClickListener { showThemeChooserDialog() }
        addMedicationButton.setOnClickListener { addMedication() }
        startDateButton.setOnClickListener { showDatePickerDialog(true) }
        endDateButton.setOnClickListener { showDatePickerDialog(false) }
        morningTimeButton.setOnClickListener { showTimePickerDialog(0) }
        noonTimeButton.setOnClickListener { showTimePickerDialog(1) }
        eveningTimeButton.setOnClickListener { showTimePickerDialog(2) }
        bedtimeTimeButton.setOnClickListener { showTimePickerDialog(3) }

        connectBoxButton.setOnClickListener {
            if (isBleConnected) {
                bluetoothLeManager.disconnect()
            } else {
                connectBoxButton.isEnabled = false
                requestBluetoothPermissionsAndScan()
            }
        }
        testCommandButton.setOnClickListener {
            if (isBleConnected) {
                bluetoothLeManager.requestStatus() // Changed from syncTime to requestStatus for testing
            } else {
                Toast.makeText(this, getString(R.string.connect_box_first), Toast.LENGTH_SHORT).show()
            }
        }
        showAllMedicationsButton.setOnClickListener {
            if (displayNotesTextView.isVisible) {
                displayNotesTextView.isGone = true
                showAllMedicationsButton.text = getString(R.string.show_all_medications)
            } else {
                showAllMedicationsInTextView()
                displayNotesTextView.isVisible = true
                showAllMedicationsButton.text = getString(R.string.hide_medication_list)
            }
        }
        showAllMedicationsButton.setOnLongClickListener {
            showMedicationListForDeletion()
            true
        }
    }

    private fun setupCalendar() {
        class DayViewContainer(view: View) : ViewContainer(view) {
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            val dotView: View = view.findViewById(R.id.calendarDayDot)
        }
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()
                container.textView.alpha = if (data.position == DayPosition.MonthDate) 1f else 0.3f
                val dateStr = data.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                container.dotView.isVisible = (dailyStatusMap[dateStr] == STATUS_ALL_TAKEN)
            }
        }
        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<ViewContainer> {
            override fun create(view: View) = ViewContainer(view)
            override fun bind(container: ViewContainer, data: CalendarMonth) {
                val textView = container.view.findViewById<TextView>(R.id.calendarMonthText)
                textView.text = DateTimeFormatter.ofPattern("yyyy MMMM").format(data.yearMonth)
            }
        }
        val currentMonth = YearMonth.now()
        calendarView.setup(currentMonth.minusMonths(12), currentMonth.plusMonths(12), DayOfWeek.SUNDAY)
        calendarView.scrollToMonth(currentMonth)
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

    private fun applySelectedTheme() {
        when (sharedPreferences.getInt(KEY_SELECTED_THEME, THEME_DEFAULT)) {
            THEME_DEFAULT -> setTheme(R.style.Theme_MedicationReminderApp)
            THEME_PURPLE -> setTheme(R.style.Theme_MedicationReminderApp_Purple)
            THEME_BLUE -> setTheme(R.style.Theme_MedicationReminderApp_Blue)
            THEME_GREEN -> setTheme(R.style.Theme_MedicationReminderApp_Green)
        }
    }

    private fun showThemeChooserDialog() {
        val themes = arrayOf(
            getString(R.string.themes_default),
            getString(R.string.themes_purple),
            getString(R.string.themes_blue),
            getString(R.string.themes_green)
        )
        val currentThemeIndex = sharedPreferences.getInt(KEY_SELECTED_THEME, THEME_DEFAULT)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_theme_title))
            .setSingleChoiceItems(themes, currentThemeIndex) { dialog, which ->
                if (which != currentThemeIndex) {
                    sharedPreferences.edit { putInt(KEY_SELECTED_THEME, which).apply() }
                    dialog.dismiss()
                    recreate()
                }
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun requestBluetoothPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else { arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) }
        val permissionsToRequest = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
        if (permissionsToRequest.isEmpty()) { bluetoothLeManager.startScan() }
        else { multiplePermissionsLauncher.launch(permissionsToRequest) }
    }

    // =================================================================================
    // 藍牙監聽器回呼 (V8 版本)
    // =================================================================================

    @SuppressLint("SetTextI18n")
    override fun onStatusUpdate(message: String) {
        runOnUiThread {
            bleStatusTextView.text = getString(R.string.ble_status_message, message)
            if (message.contains("失敗") || message.contains("超時")) {
                connectBoxButton.isEnabled = true
                connectBoxButton.text = getString(R.string.connect_to_box)
            }
        }
    }

    override fun onDeviceConnected() {
        isBleConnected = true
        runOnUiThread {
            connectBoxButton.isEnabled = true
            connectBoxButton.text = getString(R.string.disconnect_from_box)
            bleStatusTextView.text = getString(R.string.ble_status_connected_syncing)
            testCommandButton.isVisible = true
            // 連接成功後，延遲一下再請求狀態，確保 ESP32 準備好
            Handler(Looper.getMainLooper()).postDelayed({
                bluetoothLeManager.requestStatus()
            }, 500)
            // Start the sync process after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                 bluetoothLeManager.syncTime()
            }, 1000)
        }
    }

    override fun onDeviceDisconnected() {
        isBleConnected = false
        runOnUiThread {
            connectBoxButton.isEnabled = true
            connectBoxButton.text = getString(R.string.connect_to_box)
            bleStatusTextView.text = getString(R.string.ble_status_disconnected)
            testCommandButton.isGone = true
            tempTextView.text = getString(R.string.temperature_empty)
            humidityTextView.text = getString(R.string.humidity_empty)
        }
    }

    override fun onMedicationTaken(slotNumber: Int, remainingPills: Int) {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.medication_taken_report, slotNumber), Toast.LENGTH_LONG).show()

            // 根據 slotNumber 找到對應的藥物並更新庫存
            val medication = medicationList.find { it.slotNumber == slotNumber }
            medication?.let {
                it.remainingPills = remainingPills
                saveMedicationData() // 保存藥物列表的更新

                // 檢查低庫存
                checkLowStock(it)
            }

            // 更新日曆和依從率
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            dailyStatusMap[todayStr] = STATUS_ALL_TAKEN
            saveDailyStatusData()
            calendarView.notifyCalendarChanged()
            updateComplianceRate()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBoxStatusUpdate(slotMask: Byte) {
        runOnUiThread { bleStatusTextView.text = getString(R.string.status_slot_update, slotMask.toString()) }
    }

    override fun onTimeSyncAcknowledged() {
        runOnUiThread {
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val message = getString(R.string.time_sync_success, currentTime)
            bleStatusTextView.text = getString(R.string.ble_status, message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            syncAllRemindersToBox()
        }
    }

    override fun onSensorData(temperature: Float, humidity: Float) {
        runOnUiThread {
            tempTextView.text = getString(R.string.temperature_format, temperature)
            humidityTextView.text = getString(R.string.humidity_format, humidity)
            // 可以在這裡加入溫濕度超標的警告邏輯
        }
    }

    override fun onError(errorCode: Int) {
        runOnUiThread {
            val message = when(errorCode) {
                1 -> getString(R.string.error_jammed)
                2 -> getString(R.string.error_sensor)
                else -> getString(R.string.error_unknown, errorCode)
            }
            AlertDialog.Builder(this).setTitle(getString(R.string.box_anomaly_title)).setMessage(message).setPositiveButton(getString(R.string.cancel), null).show()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updateTimeSettingsVisibility(position) }
    override fun onNothingSelected(parent: AdapterView<*>?) {}

    // =================================================================================
    // V8 新功能函式
    // =================================================================================

    private fun addMedication() {
        val name = medicationNameEditText.text.toString()
        val dosageString = dosageValueTextView.text.toString()
        val totalPillsString = findViewById<EditText>(R.id.totalPillsEditText).text.toString() // 假設 UI 上有這個輸入框

        if (name.isBlank() || startDate == null || endDate == null || totalPillsString.isBlank()) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (startDate!!.after(endDate)) {
            Toast.makeText(this, getString(R.string.start_date_after_end_date), Toast.LENGTH_SHORT).show(); return
        }

        val totalPills = totalPillsString.toIntOrNull() ?: 0
        val requiredTimes = when (frequencySpinner.selectedItemPosition) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 0 }
        if (requiredTimes > 0 && selectedTimes.size != requiredTimes) {
            Toast.makeText(this, getString(R.string.set_all_medication_times), Toast.LENGTH_SHORT).show(); return
        }
        notesMap[name] = notesEditText.text.toString()

        // *** 顯示藥倉選擇對話框 ***
        showSlotChooserDialog { selectedSlot ->
            // --- 在用戶選擇藥倉後，執行後續邏輯 ---
            val newMedication = Medication(
                name = name,
                dosage = dosageString,
                frequency = frequencySpinner.selectedItem.toString(),
                startDate = startDate!!.timeInMillis,
                endDate = endDate!!.timeInMillis,
                times = selectedTimes.mapValues { it.value.timeInMillis },
                id = generateNotificationId(),
                slotNumber = selectedSlot,
                totalPills = totalPills,
                remainingPills = totalPills
            )
            medicationList.add(newMedication)
            saveAllData()
            setAlarmForMedication(newMedication) // 這會觸發藍牙同步
            Toast.makeText(this, getString(R.string.medication_added_to_slot, name, selectedSlot), Toast.LENGTH_SHORT).show()
            clearInputFields()
            if (displayNotesTextView.isVisible) { showAllMedicationsInTextView() }
        }
    }

    private fun showSlotChooserDialog(onSlotSelected: (Int) -> Unit) {
        val slots = Array(7) { getString(R.string.slot_n, it + 1) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_slot_title))
            .setItems(slots) { _, which ->
                onSlotSelected(which + 1) // 回傳 1-7
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun checkLowStock(medication: Medication) {
        val threshold = (medication.totalPills * 0.1).toInt().coerceAtLeast(1) // 低於 10% 或至少 1 顆時警告
        if (medication.remainingPills > 0 && medication.remainingPills <= threshold) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.low_stock_warning_title))
                .setMessage(getString(R.string.low_stock_warning_message, medication.name, medication.remainingPills))
                .setPositiveButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun updateComplianceRate() {
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        var totalExpected = 0
        var totalTaken = 0

        // 遍歷過去 30 天
        for (i in 0..29) {
            val day = (thirtyDaysAgo.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayStr = formatter.format(day.time)

            var dailyExpectedCount = 0
            medicationList.forEach { med ->
                if (day.timeInMillis >= med.startDate && day.timeInMillis <= med.endDate) {
                    dailyExpectedCount += med.times.size
                }
            }

            if (dailyExpectedCount > 0) {
                totalExpected += 1 // 每天算作一個單位
                if (dailyStatusMap[dayStr] == STATUS_ALL_TAKEN) {
                    totalTaken += 1
                }
            }
        }

        val rate = if (totalExpected == 0) 100 else (totalTaken * 100 / totalExpected)
        complianceRateTextView.text = getString(R.string.compliance_rate_format, rate)
        complianceProgressBar.progress = rate
    }

    private fun syncAllRemindersToBox() {
        if (!isBleConnected) return
        bluetoothLeManager.cancelAllReminders()
        Handler(Looper.getMainLooper()).postDelayed({
            medicationList.forEach { medication ->
                medication.times.forEach { (_, timeMillis) ->
                    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
                    // 使用新的 setReminder 格式
                    bluetoothLeManager.setReminder(medication.slotNumber, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), medication.totalPills)
                }
            }
            runOnUiThread { Toast.makeText(this, getString(R.string.reminders_synced_to_box), Toast.LENGTH_SHORT).show() }
        }, 800)
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
            val pendingIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarmTime = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            var nextAlarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarmTime.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, alarmTime.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            val startCalendar = Calendar.getInstance().apply { timeInMillis = medication.startDate }
            if (nextAlarmTime.before(startCalendar)) {
                nextAlarmTime = startCalendar.apply {
                    set(Calendar.HOUR_OF_DAY, alarmTime.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, alarmTime.get(Calendar.MINUTE))
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
        if (isBleConnected) { syncAllRemindersToBox() }
    }

    private fun clearInputFields() {
        medicationNameEditText.text.clear()
        frequencySpinner.setSelection(0)
        notesEditText.text.clear()
        startDate = null; endDate = null
        startDateButton.text = getString(R.string.select_start_date); endDateButton.text = getString(R.string.select_end_date)
        selectedTimes.clear(); updateSelectedTimesDisplay()
        dosageSlider.value = 1.0f
        dosageValueTextView.text = String.format(Locale.getDefault(), getString(R.string.dosage_format), dosageSlider.value)
        findViewById<EditText>(R.id.totalPillsEditText).text.clear()
    }

    private fun showAllMedicationsInTextView() {
        if (medicationList.isEmpty()) {
            displayNotesTextView.text = getString(R.string.no_medication_reminders)
            return
        }
        val builder = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeLabels = mapOf(
            0 to getString(R.string.time_label_morning),
            1 to getString(R.string.time_label_noon),
            2 to getString(R.string.time_label_evening),
            3 to getString(R.string.time_label_bedtime)
        )
        medicationList.forEachIndexed { index, med ->
            builder.append(getString(R.string.medication_info_header, index + 1))
                .append(getString(R.string.medication_info_name, med.name, med.slotNumber))
                .append(getString(R.string.medication_info_dosage, med.dosage))
                .append(getString(R.string.medication_info_stock, med.remainingPills, med.totalPills))
                .append(getString(R.string.medication_info_frequency, med.frequency))
                .append(getString(R.string.medication_info_date_range, dateFormat.format(Date(med.startDate)), dateFormat.format(Date(med.endDate))))
            if (med.times.isNotEmpty()) {
                builder.append(getString(R.string.medication_info_times))
                med.times.toSortedMap().forEach { (type, timeMillis) ->
                    builder.append(getString(R.string.medication_info_time_entry, timeLabels[type] ?: getString(R.string.time_label_default), timeFormat.format(Date(timeMillis))))
                }
            }
            notesMap[med.name]?.takeIf { it.isNotBlank() }?.let { builder.append(getString(R.string.medication_info_notes, it)) }
            builder.append("")
        }
        displayNotesTextView.text = builder.toString()
    }

    private fun showMedicationListForDeletion() {
        if (medicationList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_medication_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        val medNames = medicationList.map { "${it.name} (#${it.slotNumber})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(getString(R.string.confirm_delete_title)).setItems(medNames) { _, which ->
            confirmAndDeleteMedication(medicationList[which])
        }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun confirmAndDeleteMedication(medication: Medication) {
        AlertDialog.Builder(this).setTitle(getString(R.string.confirm_delete_title)).setMessage(getString(R.string.confirm_delete_message, medication.name))
            .setPositiveButton(getString(R.string.cancel)) { _, _ -> deleteMedication(medication) }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun deleteMedication(medication: Medication) {
        medication.times.forEach { (timeType, _) ->
            val intent = Intent(this, AlarmReceiver::class.java)
            val pIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

            if (pIntent != null) alarmManager?.cancel(pIntent)
        }
        medicationList.remove(medication); notesMap.remove(medication.name)
        saveMedicationData(); saveNotesData()
        Toast.makeText(this, getString(R.string.medication_deleted, medication.name), Toast.LENGTH_SHORT).show()
        if (displayNotesTextView.isVisible) showAllMedicationsInTextView()
        if (isBleConnected) syncAllRemindersToBox()
    }

    private fun showDatePickerDialog(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
            val fmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(sel.time)
            if (isStart) { startDate = sel; startDateButton.text = fmt }
            else { endDate = sel; endDateButton.text = fmt }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePickerDialog(type: Int) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            selectedTimes[type] = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
            updateSelectedTimesDisplay()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun updateSelectedTimesDisplay() {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val def = getString(R.string.not_set)
        morningTimeDisplay.text = selectedTimes[0]?.let { fmt.format(it.time) } ?: def
        noonTimeDisplay.text = selectedTimes[1]?.let { fmt.format(it.time) } ?: def
        eveningTimeDisplay.text = selectedTimes[2]?.let { fmt.format(it.time) } ?: def
        bedtimeTimeDisplay.text = selectedTimes[3]?.let { fmt.format(it.time) } ?: def
    }

    private fun updateTimeSettingsVisibility(pos: Int) {
        val map = mapOf(0 to listOf(true, false, false, false), 1 to listOf(true, true, false, false), 2 to listOf(true, true, true, false), 3 to listOf(true, true, true, true))
        val vis = map[pos]
        timeSettingsLayout.isVisible = vis != null
        if (vis != null) {
            morningTimeButton.isVisible = vis[0]; morningTimeDisplay.isVisible = vis[0]
            noonTimeButton.isVisible = vis[1]; noonTimeDisplay.isVisible = vis[1]
            eveningTimeButton.isVisible = vis[2]; eveningTimeDisplay.isVisible = vis[2]
            bedtimeTimeButton.isVisible = vis[3]; bedtimeTimeDisplay.isVisible = vis[3]
        } else {
            selectedTimes.clear(); updateSelectedTimesDisplay()
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

    private fun saveMedicationData() { sharedPreferences.edit { putString(KEY_MEDICATION_DATA, gson.toJson(medicationList)) } }
    private fun loadMedicationData() {
        sharedPreferences.getString(KEY_MEDICATION_DATA, null)?.let {
            try { medicationList = gson.fromJson(it, object : TypeToken<MutableList<Medication>>() {}.type) ?: mutableListOf() }
            catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse med data failed", e) }
        }
    }
    private fun saveNotesData() { sharedPreferences.edit { putString(KEY_NOTES_DATA, gson.toJson(notesMap)) } }
    private fun loadNotesData() {
        sharedPreferences.getString(KEY_NOTES_DATA, null)?.let {
            try { notesMap = gson.fromJson(it, object : TypeToken<MutableMap<String, String>>() {}.type) ?: mutableMapOf() }
            catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse notes data failed", e) }
        }
    }
    private fun saveDailyStatusData() { sharedPreferences.edit { putString(KEY_DAILY_STATUS, gson.toJson(dailyStatusMap)) } }
    private fun loadDailyStatusData() {
        sharedPreferences.getString(KEY_DAILY_STATUS, null)?.let {
            try { dailyStatusMap = gson.fromJson(it, object : TypeToken<MutableMap<String, Int>>() {}.type) ?: mutableMapOf() }
            catch (e: JsonSyntaxException) { Log.e("M_Activity", "Parse daily status failed", e) }
        }
    }

    data class Medication(
        val name: String,
        val dosage: String,
        val frequency: String,
        val startDate: Long,
        val endDate: Long,
        val times: Map<Int, Long>,
        val id: Int,
        // *** V8 新增 ***
        val slotNumber: Int,    // 藥倉編號 (1-7)
        var totalPills: Int,    // 藥物總數 (可變)
        var remainingPills: Int // 剩餘藥量 (可變)
    )

    companion object {
        const val CHANNEL_ID = "medication_reminder_channel"
        const val PREFS_NAME = "MedicationReminderAppPrefs"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val KEY_MEDICATION_DATA = "medication_data"
        const val KEY_NOTES_DATA = "notes_data"
        const val KEY_DAILY_STATUS = "daily_status"
        const val STATUS_ALL_TAKEN = 2
        const val THEME_DEFAULT = 0
        const val THEME_PURPLE = 1
        const val THEME_BLUE = 2
        const val THEME_GREEN = 3
        private val random = Random()
        fun generateNotificationId(): Int = random.nextInt(1_000_000)
    }
}
