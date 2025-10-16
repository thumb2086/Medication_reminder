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
        // ... (省略，與上一版相同)
    }

    private fun requestAppPermissions() {
        // ... (省略，與上一版相同)
    }
    
    private fun applySelectedTheme() {
        // ... (省略，與上一版相同)
    }

    private fun showThemeChooserDialog() {
        // ... (省略，與上一版相同)
    }
    
    private fun requestBluetoothPermissionsAndScan() {
        // ... (省略，與上一版相同)
    }
    
    override fun onStatusUpdate(message: String) { /* ... */ }
    override fun onDeviceConnected() { /* ... */ }
    override fun onDeviceDisconnected() { /* ... */ }
    override fun onMedicationTaken(slotNumber: Int) { /* ... */ }

    override fun onBoxStatusUpdate(slotMask: Byte) {
        runOnUiThread {
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
        // ... (省略，與上一版相同)
    }
    
    private fun determineSlotMaskFor(medication: Medication): Byte {
        if (medication.slotNumber in 1..7) {
            return (1 shl (medication.slotNumber - 1)).toByte()
        }
        return 0
    }
    
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { /* ... */ }
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
            .setPositiveButton("確定") { dialog, _ ->
                onSlotSelected(selectedSlot + 1)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }

    private fun setAlarmForMedication(medication: Medication) {
        // ... (省略，與上一版相同)
    }

    private fun clearAndExitEditMode() {
        medicationNameEditText.text.clear()
        frequencySpinner.setSelection(0)
        notesEditText.text.clear()
        startDate = null; endDate = null
        startDateButton.text = "選擇開始日期"; endDateButton.text = "選擇結束日期"
        selectedTimes.clear(); updateSelectedTimesDisplay()
        dosageSlider.value = 1.0f; dosageValueTextView.text = String.format("%.1f", dosageSlider.value)
        dosageUnitSpinner.setSelection(0)
        
        editingMedication = null
        addMedicationButton.text = "新增藥物提醒"
    }

    private fun showAllMedicationsInTextView() {
        // ... (省略，與上一版相同)
    }

    private fun showMedicationListForDeletion() {
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
        // ... (省略，與上一版相同)
    }
    
    private fun deleteMedication(medication: Medication) {
        // ... (省略，與上一版相同)
    }

    private fun showDatePickerDialog(isStart: Boolean) { /* ... */ }
    private fun showTimePickerDialog(type: Int) { /* ... */ }
    private fun updateSelectedTimesDisplay() { /* ... */ }
    private fun updateTimeSettingsVisibility(pos: Int) { /* ... */ }
    private fun createNotificationChannel() { /* ... */ }
    private fun showPermissionDeniedDialog() { /* ... */ }
    
    private fun loadAllData() { /* ... */ }
    private fun saveAllData() { /* ... */ }
    private fun saveMedicationData() { /* ... */ }
    private fun loadMedicationData() { /* ... */ }
    private fun saveNotesData() { /* ... */ }
    private fun loadNotesData() { /* ... */ }
    private fun saveDailyStatusData() { /* ... */ }
    private fun loadDailyStatusData() { /* ... */ }

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