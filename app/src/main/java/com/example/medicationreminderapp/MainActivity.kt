package com.example.medicationreminderapp

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var dosageUnitSpinner: Spinner
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
    private lateinit var slotStatusContainer: LinearLayout

    // --- 數據 & 邏輯相關屬性 ---
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private var alarmManager: AlarmManager? = null
    private var medicationList: MutableList<Medication> = mutableListOf()
    private var notesMap: MutableMap<String, String> = mutableMapOf()
    private var dailyStatusMap: MutableMap<String, Int> = mutableMapOf()
    private var isBleConnected = false
    private var editingMedication: Medication? = null

    private var startDate: Calendar? = null
    private var endDate: Calendar? = null
    private val selectedTimes = mutableMapOf<Int, Calendar>()

    // --- 權限請求啟動器 ---
    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) { Toast.makeText(this, "通知權限被拒絕，提醒功能可能無法正常運作", Toast.LENGTH_LONG).show() }
    }
    private val multiplePermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            bluetoothLeManager.startScan()
        } else {
            Toast.makeText(this, "需要藍牙和定位權限才能連接藥盒", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applySelectedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeUI()
        initializeDataAndLoad()
        createNotificationChannel()
        setupListeners()
        setupCalendar()
        requestAppPermissions()
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
        dosageUnitSpinner = findViewById(R.id.dosageUnitSpinner)
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
        slotStatusContainer = findViewById(R.id.slotStatusContainer)
        setupSlotViews()
    }

    private fun initializeDataAndLoad() {
        gson = Gson()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        bluetoothLeManager = BluetoothLeManager(this, this)
        loadAllData()
    }

    private fun setupListeners() {
        ArrayAdapter.createFromResource(this, R.array.medication_frequency_options, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            frequencySpinner.adapter = it
        }
        frequencySpinner.onItemSelectedListener = this

        ArrayAdapter.createFromResource(this, R.array.dosage_unit_options, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dosageUnitSpinner.adapter = it
        }

        medicationNameEditText.addTextChangedListener { notesEditText.setText(notesMap[it.toString()] ?: "") }
        dosageSlider.addOnChangeListener { _, value, _ -> dosageValueTextView.text = String.format("%.1f", value) }
        dosageValueTextView.text = String.format("%.1f", dosageSlider.value)

        settingsButton.setOnClickListener { showThemeChooserDialog() }
        addMedicationButton.setOnClickListener { addOrUpdateMedication() }
        startDateButton.setOnClickListener { showDatePickerDialog(true) }
        endDateButton.setOnClickListener { showDatePickerDialog(false) }
        morningTimeButton.setOnClickListener { showTimePickerDialog(0) }
        noonTimeButton.setOnClickListener { showTimePickerDialog(1) }
        eveningTimeButton.setOnClickListener { showTimePickerDialog(2) }
        bedtimeTimeButton.setOnClickListener { showTimePickerDialog(3) }
        connectBoxButton.setOnClickListener {
            if (isBleConnected) bluetoothLeManager.disconnect()
            else {
                connectBoxButton.isEnabled = false
                requestBluetoothPermissionsAndScan()
            }
        }
        testCommandButton.setOnClickListener {
            if (isBleConnected) bluetoothLeManager.syncTime()
            else Toast.makeText(this, "請先連接藥盒", Toast.LENGTH_SHORT).show()
        }
        showAllMedicationsButton.setOnClickListener {
            if (displayNotesTextView.isVisible) {
                displayNotesTextView.isGone = true
                showAllMedicationsButton.text = "顯示所有藥物"
            } else {
                showAllMedicationsInTextView()
                displayNotesTextView.isVisible = true
                showAllMedicationsButton.text = "隱藏藥物列表"
            }
        }
         showAllMedicationsButton.setOnLongClickListener {
            showMedicationListForActions()
            true
        }
    }
    
    private fun setupSlotViews() {
        slotStatusContainer.removeAllViews()
        for (i in 1..7) {
            val imageView = ImageView(this).apply {
                id = View.generateViewId()
                setImageResource(R.drawable.ic_slot_empty)
                tag = i 
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            slotStatusContainer.addView(imageView)
        }
    }

    private fun setupCalendar() {
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()
                if (day.position == DayPosition.MonthDate) {
                    container.textView.alpha = 1.0f
                } else {
                    container.textView.alpha = 0.3f
                }
                val dateStr = day.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                container.dotView.isVisible = (dailyStatusMap[dateStr] == STATUS_ALL_TAKEN)
            }
        }
        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<ViewContainer> {
            override fun create(view: View) = ViewContainer(view)
            override fun bind(container: ViewContainer, month: CalendarMonth) {
                val textView = container.view.findViewById<TextView>(R.id.calendarMonthText)
                val formatter = DateTimeFormatter.ofPattern("yyyy MMMM")
                textView.text = formatter.format(month.yearMonth)
            }
        }
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(12)
        val endMonth = currentMonth.plusMonths(12)
        calendarView.setup(startMonth, endMonth, DayOfWeek.SUNDAY)
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
        val themes = arrayOf("跟隨系統 (預設)", "夢幻紫", "天空藍", "活力綠")
        val currentThemeIndex = sharedPreferences.getInt(KEY_SELECTED_THEME, THEME_DEFAULT)
        AlertDialog.Builder(this)
            .setTitle("選擇應用程式主題")
            .setSingleChoiceItems(themes, currentThemeIndex) { dialog, which ->
                if (which != currentThemeIndex) {
                    sharedPreferences.edit { putInt(KEY_SELECTED_THEME, which).apply() }
                    dialog.dismiss()
                    recreate()
                }
            }
            .setNegativeButton("取消", null).show()
    }
    
    private fun requestBluetoothPermissionsAndScan() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissionsToRequest.isEmpty()) {
            bluetoothLeManager.startScan()
        } else {
            multiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }
    
    override fun onStatusUpdate(message: String) {
        runOnUiThread {
            bleStatusTextView.text = "狀態：$message"
            if (message.contains("失敗") || message.contains("超時")) {
                connectBoxButton.isEnabled = true
                connectBoxButton.text = "連接智慧藥盒"
            }
        }
    }
    override fun onDeviceConnected() {
        isBleConnected = true
        runOnUiThread {
            connectBoxButton.isEnabled = true
            connectBoxButton.text = "斷開與藥盒的連接"
            bleStatusTextView.text = "藍牙狀態：已連接至 SmartMedBox"
            testCommandButton.isVisible = true
            syncAllRemindersToBox()
        }
    }
    override fun onDeviceDisconnected() {
        isBleConnected = false
        runOnUiThread {
            connectBoxButton.isEnabled = true
            connectBoxButton.text = "連接智慧藥盒"
            bleStatusTextView.text = "藍牙狀態：未連接"
            testCommandButton.isGone = true
        }
    }
    override fun onMedicationTaken(slotNumber: Int) {
        runOnUiThread {
            val message = "成功接收到事件：第 $slotNumber 號藥倉的藥已被取出！"
            bleStatusTextView.text = message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            dailyStatusMap[todayStr] = STATUS_ALL_TAKEN
            saveDailyStatusData()
            calendarView.notifyCalendarChanged()
        }
    }
    override fun onBoxStatusUpdate(slotMask: Byte) {
        runOnUiThread {
             Log.d("BLE_STATUS", "藥倉狀態更新，掩碼: $slotMask")
            bleStatusTextView.text = "狀態：收到藥倉狀態更新 ($slotMask)"
            for (i in 0..6) {
                val imageView = slotStatusContainer.findViewWithTag<ImageView>(i + 1)
                val hasMedication = (slotMask.toInt() shr i) and 1 == 1
                imageView?.setImageResource(
                    if (hasMedication) R.drawable.ic_slot_filled else R.drawable.ic_slot_empty
                )
            }
        }
    }
    
    private fun syncAllRemindersToBox() {
        if (!isBleConnected) return
        runOnUiThread {
            bleStatusTextView.text = "狀態：正在同步提醒至藥盒..."
            Toast.makeText(this, "開始同步提醒...", Toast.LENGTH_SHORT).show()
        }
        Log.d("Sync", "Step 1: Sending 'Cancel All' command.")
        bluetoothLeManager.cancelAllReminders()
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("Sync", "Step 2: Sending new reminders after delay.")
            medicationList.forEach { medication ->
                medication.times.forEach { (_, timeMillis) ->
                    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val minute = calendar.get(Calendar.MINUTE)
                    val slotMask = determineSlotMaskFor(medication)
                    if (slotMask > 0) {
                        bluetoothLeManager.setReminder(slotMask, hour, minute)
                    }
                }
            }
            runOnUiThread {
                bleStatusTextView.text = "藍牙狀態：已連接 (提醒已同步)"
                Toast.makeText(this, "提醒同步完成！", Toast.LENGTH_SHORT).show()
            }
        }, 800)
    }
    
    private fun determineSlotMaskFor(medication: Medication): Byte {
        if (medication.slotNumber in 1..7) {
            return (1 shl (medication.slotNumber - 1)).toByte()
        }
        return 0
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        updateTimeSettingsVisibility(position)
    }
    override fun onNothingSelected(parent: AdapterView<*>?) {}

    private fun addOrUpdateMedication() {
        val name = medicationNameEditText.text.toString()
        val dosageValue = dosageSlider.value
        val dosageUnit = dosageUnitSpinner.selectedItem.toString()
        val dosageString = String.format("%.1f %s", dosageValue, dosageUnit)

        if (name.isBlank() || startDate == null || endDate == null) {
            Toast.makeText(this, "請填寫藥物名稱及日期範圍", Toast.LENGTH_SHORT).show()
            return
        }
        if (startDate!!.after(endDate)) {
            Toast.makeText(this, "開始日期不能晚於結束日期", Toast.LENGTH_SHORT).show()
            return
        }
        val requiredTimes = when (frequencySpinner.selectedItemPosition) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 0 }
        if (requiredTimes > 0 && selectedTimes.size != requiredTimes) {
            Toast.makeText(this, "請設定所有必要的服藥時間", Toast.LENGTH_SHORT).show()
            return
        }

        showSlotChooserDialog { selectedSlot ->
            val medicationToSave = editingMedication?.copy(
                name = name,
                dosage = dosageString,
                frequency = frequencySpinner.selectedItem.toString(),
                startDate = startDate!!.timeInMillis,
                endDate = endDate!!.timeInMillis,
                times = selectedTimes.mapValues { it.value.timeInMillis },
                slotNumber = selectedSlot
            ) ?: Medication(
                name = name,
                dosage = dosageString,
                frequency = frequencySpinner.selectedItem.toString(),
                startDate = startDate!!.timeInMillis,
                endDate = endDate!!.timeInMillis,
                times = selectedTimes.mapValues { it.value.timeInMillis },
                id = generateNotificationId(),
                slotNumber = selectedSlot
            )

            if (editingMedication != null) {
                val index = medicationList.indexOfFirst { it.id == editingMedication!!.id }
                if (index != -1) medicationList[index] = medicationToSave
                Toast.makeText(this, "藥物 '${name}' 已更新", Toast.LENGTH_SHORT).show()
            } else {
                medicationList.add(medicationToSave)
                Toast.makeText(this, "藥物 '${name}' 已新增", Toast.LENGTH_SHORT).show()
            }
            
            notesMap[name] = notesEditText.text.toString()
            saveMedicationData(); saveNotesData()
            setAlarmForMedication(medicationToSave)
            clearAndExitEditMode()
            if (displayNotesTextView.isVisible) showAllMedicationsInTextView()
             if (isBleConnected) syncAllRemindersToBox()
        }
    }
    
    private fun showSlotChooserDialog(onSlotSelected: (Int) -> Unit) {
        val slots = (1..7).map { "第 $it 號藥倉" }.toTypedArray()
        var selectedSlot = editingMedication?.slotNumber?.minus(1) ?: 0
        AlertDialog.Builder(this)
            .setTitle("請為藥物選擇藥倉")
            .setSingleChoiceItems(slots, selectedSlot) { _, which ->
                selectedSlot = which
            }
            .setPositiveButton("確定") { _, _ ->
                onSlotSelected(selectedSlot + 1)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }
    
    private fun setAlarmForMedication(medication: Medication) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel old alarms for this medication first
         medication.times.keys.forEach { timeType ->
            val intent = Intent(this, AlarmReceiver::class.java)
            val pIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pIntent != null) am.cancel(pIntent)
        }

        medication.times.forEach { (timeType, timeMillis) ->
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("medicationName", medication.name); putExtra("dosage", medication.dosage)
                putExtra("notificationId", medication.id + timeType); putExtra("medicationEndDate", medication.endDate)
                putExtra("originalAlarmTimeOfDay", timeMillis)
            }
            val pendingIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            
            val alarmTime = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            var nextAlarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarmTime.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, alarmTime.get(Calendar.MINUTE)); set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            
            val startCalendar = Calendar.getInstance().apply { timeInMillis = medication.startDate }
            if (nextAlarmTime.before(startCalendar)) {
                nextAlarmTime = startCalendar.apply {
                    set(Calendar.HOUR_OF_DAY, alarmTime.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, alarmTime.get(Calendar.MINUTE))
                }
                 if (nextAlarmTime.before(Calendar.getInstance())) { nextAlarmTime.add(Calendar.DATE, 1) }
            }

            val endCalendar = Calendar.getInstance().apply { timeInMillis = medication.endDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
            if (nextAlarmTime.before(endCalendar)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime.timeInMillis, pendingIntent)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime.timeInMillis, pendingIntent)
                    }
                    Log.d("AlarmScheduler", "為 ${medication.name} 設定鬧鐘在 ${Date(nextAlarmTime.timeInMillis)}")
                } catch (e: SecurityException) { Log.e("AlarmScheduler", "無法設定精確鬧鐘", e) }
            }
        }
    }

    private fun clearAndExitEditMode() {
        medicationNameEditText.text.clear()
        frequencySpinner.setSelection(0)
        notesEditText.text.clear()
        startDate = null; endDate = null
        startDateButton.text = "選擇開始日期"; endDateButton.text = "選擇結束日期"
        selectedTimes.clear(); updateSelectedTimesDisplay()
        dosageSlider.value = 1.0f
        dosageUnitSpinner.setSelection(0)
        
        editingMedication = null
        addMedicationButton.text = "新增藥物提醒"
    }

    private fun showAllMedicationsInTextView() {
        if (medicationList.isEmpty()) { displayNotesTextView.text = "目前沒有藥物提醒。"; return }
        val builder = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeLabels = mapOf(0 to "早上", 1 to "中午", 2 to "晚上", 3 to "睡前")
        medicationList.forEachIndexed { index, med ->
            builder.append("--- 藥物 ${index + 1} (藥倉 #${med.slotNumber}) ---
")
                .append("名稱: ${med.name}
").append("劑量: ${med.dosage}
")
                .append("頻率: ${med.frequency}
")
                .append("日期區間: ${dateFormat.format(Date(med.startDate))} 至 ${dateFormat.format(Date(med.endDate))}
")
            if (med.times.isNotEmpty()) {
                builder.append("服藥時間:
")
                med.times.toSortedMap().forEach { (type, timeMillis) ->
                    builder.append("  - ${timeLabels[type] ?: "時間"}: ${timeFormat.format(Date(timeMillis))}
")
                }
            }
            notesMap[med.name]?.takeIf { it.isNotBlank() }?.let { builder.append("備註: $it
") }
            builder.append("
")
        }
        displayNotesTextView.text = builder.toString()
    }

    private fun showMedicationListForActions() {
        if (medicationList.isEmpty()) { Toast.makeText(this, "沒有藥物", Toast.LENGTH_SHORT).show(); return }
        val medNames = medicationList.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("選擇藥物進行操作").setItems(medNames) { _, which ->
            showActionDialogFor(medicationList[which])
        }.setNegativeButton("取消", null).show()
    }

    private fun showActionDialogFor(medication: Medication) {
        val options = arrayOf("編輯提醒", "刪除此藥物")
        AlertDialog.Builder(this)
            .setTitle(medication.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startEditMode(medication)
                    1 -> confirmAndDeleteMedication(medication)
                }
            }
            .show()
    }
    
    private fun startEditMode(medication: Medication) {
        editingMedication = medication
        addMedicationButton.text = "更新提醒"

        medicationNameEditText.setText(medication.name)
        notesEditText.setText(notesMap[medication.name])
        
        val dosageParts = medication.dosage.split(" ")
        if (dosageParts.size == 2) {
            dosageSlider.value = dosageParts[0].toFloatOrNull() ?: 1.0f
            val unitIndex = (dosageUnitSpinner.adapter as ArrayAdapter<String>).getPosition(dosageParts[1])
            if (unitIndex >= 0) dosageUnitSpinner.setSelection(unitIndex)
        }
        
        val freqIndex = (frequencySpinner.adapter as ArrayAdapter<String>).getPosition(medication.frequency)
        if (freqIndex >= 0) frequencySpinner.setSelection(freqIndex)
        
        startDate = Calendar.getInstance().apply { timeInMillis = medication.startDate }
        endDate = Calendar.getInstance().apply { timeInMillis = medication.endDate }
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        startDateButton.text = dateFormat.format(startDate!!.time)
        endDateButton.text = dateFormat.format(endDate!!.time)
        
        selectedTimes.clear()
        selectedTimes.putAll(medication.times.mapValues { Calendar.getInstance().apply { timeInMillis = it.value } })
        updateSelectedTimesDisplay()

        Toast.makeText(this, "編輯模式：修改後請點擊更新", Toast.LENGTH_SHORT).show()
    }

    private fun confirmAndDeleteMedication(medication: Medication) {
        AlertDialog.Builder(this).setTitle("確認刪除").setMessage("您確定要刪除藥物提醒 '${medication.name}' 嗎？")
            .setPositiveButton("確定") { _, _ -> deleteMedication(medication) }
            .setNegativeButton("取消", null).show()
    }
    
    private fun deleteMedication(medication: Medication) {
        medication.times.forEach { (timeType, _) ->
            val intent = Intent(this, AlarmReceiver::class.java)
            val pIntent = PendingIntent.getBroadcast(this, medication.id + timeType, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pIntent != null) {
                alarmManager?.cancel(pIntent)
                pIntent.cancel()
            }
        }
        medicationList.remove(medication); notesMap.remove(medication.name)
        saveMedicationData(); saveNotesData()
        Toast.makeText(this, "'${medication.name}' 已刪除", Toast.LENGTH_SHORT).show()
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
        val def = "未設定"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "藥物提醒"
            val ch = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "用於藥物提醒的通知"; enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this).setTitle("權限被拒絕").setMessage("應用程式需要通知權限才能運作，請至設定開啟。")
            .setPositiveButton("前往設定") { _, _ ->
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(i)
            }.setNegativeButton("取消", null).show()
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
        val id: Int,
        var name: String, 
        var dosage: String,
        var frequency: String, 
        var startDate: Long, 
        var endDate: Long, 
        var times: Map<Int, Long>,
        var slotNumber: Int
    )

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
        val dotView: View = view.findViewById(R.id.calendarDayDot)
    }
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