package com.example.medicationreminderapp.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.MainActivity
import com.example.medicationreminderapp.Medication
import com.example.medicationreminderapp.R
import com.example.medicationreminderapp.databinding.FragmentReminderBinding
import com.example.medicationreminderapp.databinding.MedicationInputItemBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ReminderFragment : Fragment(), AdapterView.OnItemSelectedListener {

    private var _binding: FragmentReminderBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private var isEditing = false
    private var medicationAddQueue = mutableListOf<Medication>()
    private var progressDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        updateQuantitySpinner()
        binding.quantitySpinner.onItemSelectedListener = this

        binding.connectBoxButton.setOnClickListener { (activity as? MainActivity)?.requestBluetoothPermissionsAndScan() }
        binding.addMedicationButton.setOnClickListener {
            if (isEditing) {
                updateMedication()
            } else {
                addMedications()
            }
        }
        binding.startDateButton.setOnClickListener { showDatePickerDialog(true) }
        binding.endDateButton.setOnClickListener { showDatePickerDialog(false) }
        binding.morningTimeButton.setOnClickListener { showTimePickerDialog(0) }
        binding.noonTimeButton.setOnClickListener { showTimePickerDialog(1) }
        binding.eveningTimeButton.setOnClickListener { showTimePickerDialog(2) }
        binding.bedtimeTimeButton.setOnClickListener { showTimePickerDialog(3) }

        binding.showAllMedicationsButton.setOnClickListener {
            if (binding.displayNotesTextView.isVisible) {
                binding.displayNotesTextView.isVisible = false
                binding.showAllMedicationsButton.text = getString(R.string.show_all_medications)
            } else {
                showAllMedicationsInTextView()
                binding.displayNotesTextView.isVisible = true
                binding.showAllMedicationsButton.text = getString(R.string.hide_medication_list)
            }
        }

        binding.editMedicationButton.setOnClickListener { showMedicationListForAction(isEdit = true) }
        binding.deleteMedicationButton.setOnClickListener { showMedicationListForAction(isEdit = false) }
    }

    private fun observeViewModel() {
        viewModel.bleStatus.observe(viewLifecycleOwner) { binding.bleStatusTextView.text = getString(R.string.ble_status, it) }
        viewModel.isBleConnected.observe(viewLifecycleOwner) {
            binding.connectBoxButton.text = if (it) getString(R.string.disconnect_from_box) else getString(R.string.connect_to_box)
        }
        viewModel.medicationList.observe(viewLifecycleOwner) {
            if (binding.displayNotesTextView.isVisible) {
                showAllMedicationsInTextView()
            }
            updateQuantitySpinner()
        }
        viewModel.guidedFillConfirmation.observe(viewLifecycleOwner) { confirmed ->
            if (confirmed) {
                handleGuidedFillConfirmation()
                viewModel.onGuidedFillConfirmationConsumed()
            }
        }
    }

    private fun addMedications() {
        if (medicationAddQueue.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please wait for the current process to finish.", Toast.LENGTH_SHORT).show()
            return
        }

        val medicationViews = (0 until binding.medicationInputContainer.childCount).map {
            binding.medicationInputContainer.getChildAt(it)
        }
        medicationAddQueue.clear()

        for (view in medicationViews) {
            val itemBinding = MedicationInputItemBinding.bind(view)
            val name = itemBinding.medicationNameEditText.text.toString()
            val dosage = itemBinding.dosageValueTextView.text.toString()
            val frequency = itemBinding.frequencySpinner.selectedItem.toString()
            val slot = itemBinding.slotSpinner.selectedItemPosition + 1

            if (name.isBlank() || viewModel.startDate.value == null || viewModel.endDate.value == null) {
                Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return
            }
            if (viewModel.startDate.value!!.after(viewModel.endDate.value)) {
                Toast.makeText(requireContext(), getString(R.string.start_date_after_end_date), Toast.LENGTH_SHORT).show()
                return
            }
            val requiredTimes = when (itemBinding.frequencySpinner.selectedItemPosition) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 0 }
            if (requiredTimes > 0 && viewModel.selectedTimes.value?.size != requiredTimes) {
                Toast.makeText(requireContext(), getString(R.string.set_all_medication_times), Toast.LENGTH_SHORT).show()
                return
            }

            val diff = viewModel.endDate.value!!.timeInMillis - viewModel.startDate.value!!.timeInMillis
            val days = TimeUnit.MILLISECONDS.toDays(diff) + 1
            val timesPerDay = itemBinding.frequencySpinner.selectedItemPosition + 1
            val dosagePerTime = itemBinding.dosageValueTextView.text.toString().split(" ").first().toFloatOrNull() ?: 1.0f
            val totalPills = (days * timesPerDay * dosagePerTime).roundToInt()

            val originalName = itemBinding.medicationNameEditText.tag as? String
            val isSlotOccupied = viewModel.medicationList.value?.any { it.slotNumber == slot && it.name != originalName } ?: false
            if (isSlotOccupied) {
                AlertDialog.Builder(requireContext()).setTitle(getString(R.string.slot_occupied_title)).setMessage(getString(R.string.slot_occupied_message, slot)).setPositiveButton(R.string.ok, null).show()
                medicationAddQueue.clear()
                return
            }

            medicationAddQueue.add(Medication(
                name = name, dosage = dosage, frequency = frequency, startDate = viewModel.startDate.value!!.timeInMillis,
                endDate = viewModel.endDate.value!!.timeInMillis, times = viewModel.selectedTimes.value!!.mapValues { it.value.timeInMillis },
                id = MainActivity.generateNotificationId(), slotNumber = slot, totalPills = totalPills, remainingPills = totalPills
            ))
        }

        if (medicationAddQueue.isNotEmpty()) {
            processNextInQueue()
        }
    }

    private fun processNextInQueue() {
        val medication = medicationAddQueue.firstOrNull() ?: return
        (activity as? MainActivity)?.rotateToSlot(medication.slotNumber)
        progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Guided Filling")
            .setMessage("Now rotating to slot #${medication.slotNumber}. Please place the medication inside and press the button on the box to confirm.")
            .setCancelable(false)
            .show()
    }

    private fun handleGuidedFillConfirmation() {
        progressDialog?.dismiss()
        val addedMedication = medicationAddQueue.removeFirstOrNull() ?: return
        (activity as? MainActivity)?.addMedication(addedMedication)

        if (medicationAddQueue.isEmpty()) {
            Toast.makeText(requireContext(), "All medications added!", Toast.LENGTH_SHORT).show()
            clearInputFields()
            (activity as? MainActivity)?.syncRemindersToBox() // Final, full sync
        } else {
            processNextInQueue()
        }
    }


    private fun showDatePickerDialog(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
            val fmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(sel.time)
            if (isStart) {
                viewModel.startDate.value = sel; binding.startDateButton.text = fmt
            } else {
                viewModel.endDate.value = sel; binding.endDateButton.text = fmt
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePickerDialog(type: Int) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m ->
            val selected = viewModel.selectedTimes.value?.toMutableMap() ?: mutableMapOf()
            selected[type] = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
            viewModel.selectedTimes.value = selected
            updateSelectedTimesDisplay()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun updateSelectedTimesDisplay() {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val def = getString(R.string.not_set)
        binding.morningTimeDisplay.text = viewModel.selectedTimes.value?.get(0)?.let { fmt.format(it.time) } ?: def
        binding.noonTimeDisplay.text = viewModel.selectedTimes.value?.get(1)?.let { fmt.format(it.time) } ?: def
        binding.eveningTimeDisplay.text = viewModel.selectedTimes.value?.get(2)?.let { fmt.format(it.time) } ?: def
        binding.bedtimeTimeDisplay.text = viewModel.selectedTimes.value?.get(3)?.let { fmt.format(it.time) } ?: def
    }

    private fun updateTimeSettingsVisibility(pos: Int) {
        val map = mapOf(0 to listOf(true, false, false, false), 1 to listOf(true, true, false, false), 2 to listOf(true, true, true, false), 3 to listOf(true, true, true, true))
        val vis = map[pos]
        binding.timeSettingsLayout.isVisible = vis != null
        if (vis != null) {
            binding.morningTimeButton.isVisible = vis[0]; binding.morningTimeDisplay.isVisible = vis[0]
            binding.noonTimeButton.isVisible = vis[1]; binding.noonTimeDisplay.isVisible = vis[1]
            binding.eveningTimeButton.isVisible = vis[2]; binding.eveningTimeDisplay.isVisible = vis[2]
            binding.bedtimeTimeButton.isVisible = vis[3]; binding.bedtimeTimeDisplay.isVisible = vis[3]
        } else {
            viewModel.selectedTimes.value = mutableMapOf(); updateSelectedTimesDisplay()
        }
    }

    private fun showAllMedicationsInTextView() {
        val medList = viewModel.medicationList.value ?: emptyList()
        if (medList.isEmpty()) {
            binding.displayNotesTextView.text = getString(R.string.no_medication_reminders); return
        }
        val builder = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeLabels = mapOf(0 to getString(R.string.time_label_morning), 1 to getString(R.string.time_label_noon), 2 to getString(R.string.time_label_evening), 3 to getString(R.string.time_label_bedtime))
        medList.forEachIndexed { index, med ->
            builder.append("--- 藥物 ${index + 1} ---\n")
                .append("名稱: ${med.name} (藥倉: #${med.slotNumber})\n")
                .append("劑量: ${med.dosage}\n")
                .append("庫存: ${med.remainingPills} / ${med.totalPills} 顆\n")
                .append("頻率: ${med.frequency}\n")
                .append("日期: ${dateFormat.format(Date(med.startDate))} 至 ${dateFormat.format(Date(med.endDate))}\n")
            if (med.times.isNotEmpty()) {
                builder.append("時間:\n")
                med.times.toSortedMap().forEach { (type, timeMillis) -> builder.append("  - ${timeLabels[type] ?: "時間"}: ${timeFormat.format(Date(timeMillis))}\n") }
            }
            builder.append("\n")
        }
        binding.displayNotesTextView.text = builder.toString()
    }

    private fun showMedicationListForAction(isEdit: Boolean) {
        val medList = viewModel.medicationList.value ?: emptyList()
        if (medList.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_medication_to_modify), Toast.LENGTH_SHORT).show(); return
        }
        val medNames = medList.map { "${it.name} (#${it.slotNumber})" }.toTypedArray()
        val title = if (isEdit) getString(R.string.select_medication_to_edit) else getString(R.string.select_medication_to_delete)

        AlertDialog.Builder(requireContext()).setTitle(title).setItems(medNames) { _, which ->
            if (isEdit) {
                editMedication(medList[which])
            } else {
                confirmAndDeleteMedication(medList[which])
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun editMedication(medication: Medication) {
        isEditing = true
        binding.quantitySpinner.setSelection(0, false)
        binding.medicationInputContainer.removeAllViews()
        val itemBinding = MedicationInputItemBinding.inflate(LayoutInflater.from(requireContext()), binding.medicationInputContainer, false)
        binding.medicationInputContainer.addView(itemBinding.root)

        ArrayAdapter.createFromResource(requireContext(), R.array.medication_frequency_options, android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemBinding.frequencySpinner.adapter = adapter
            itemBinding.frequencySpinner.setSelection(adapter.getPosition(medication.frequency).coerceAtLeast(0))
        }
        itemBinding.frequencySpinner.onItemSelectedListener = this

        val slotOptions = (1..8).map { getString(R.string.slot_n, it) }.toTypedArray()
        val slotAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, slotOptions)
        slotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemBinding.slotSpinner.adapter = slotAdapter
        itemBinding.slotSpinner.setSelection((medication.slotNumber - 1).coerceIn(0, 7))

        itemBinding.medicationNameEditText.setText(medication.name)
        itemBinding.medicationNameEditText.tag = medication.name
        
        val currentDosage = medication.dosage.split(" ").first().toFloatOrNull() ?: 1.0f
        itemBinding.dosageSlider.value = currentDosage
        itemBinding.dosageValueTextView.text = getString(R.string.dosage_format, currentDosage)
        itemBinding.dosageSlider.addOnChangeListener { _, value, _ ->
            itemBinding.dosageValueTextView.text = getString(R.string.dosage_format, value)
        }

        viewModel.startDate.value = Calendar.getInstance().apply { timeInMillis = medication.startDate }
        viewModel.endDate.value = Calendar.getInstance().apply { timeInMillis = medication.endDate }
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        binding.startDateButton.text = sdf.format(viewModel.startDate.value!!.time)
        binding.endDateButton.text = sdf.format(viewModel.endDate.value!!.time)

        val selectedTimes = mutableMapOf<Int, Calendar>()
        medication.times.forEach { (type, millis) -> selectedTimes[type] = Calendar.getInstance().apply { timeInMillis = millis } }
        viewModel.selectedTimes.value = selectedTimes
        updateSelectedTimesDisplay()

        binding.addMedicationButton.text = getString(R.string.update_medication)
        Toast.makeText(requireContext(), getString(R.string.editing_medication, medication.name), Toast.LENGTH_SHORT).show()
    }

    private fun confirmAndDeleteMedication(medication: Medication) {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_delete_title)).setMessage(getString(R.string.confirm_delete_message, medication.name))
            .setPositiveButton(R.string.delete) { _, _ -> (activity as? MainActivity)?.deleteMedication(medication) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun clearInputFields() {
        binding.medicationInputContainer.removeAllViews()
        binding.quantitySpinner.setSelection(0)
        binding.notesEditText.setText("")
        viewModel.startDate.value = null; viewModel.endDate.value = null
        binding.startDateButton.text = getString(R.string.select_start_date)
        binding.endDateButton.text = getString(R.string.select_end_date)
        viewModel.selectedTimes.value = mutableMapOf()
        updateSelectedTimesDisplay()
        binding.addMedicationButton.text = getString(R.string.add_medication_reminder)
        isEditing = false
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        when (parent?.id) {
            R.id.quantitySpinner -> {
                if (isEditing) return
                val quantity = position + 1
                binding.medicationInputContainer.removeAllViews()
                val occupiedSlots = viewModel.medicationList.value?.map { it.slotNumber } ?: emptyList()
                var nextAvailableSlot = 1
                repeat(quantity) {
                    val inflater = LayoutInflater.from(requireContext())
                    val itemBinding = MedicationInputItemBinding.inflate(inflater, binding.medicationInputContainer, false)

                    itemBinding.dosageValueTextView.text = getString(R.string.dosage_format, itemBinding.dosageSlider.value)
                    itemBinding.dosageSlider.addOnChangeListener { _, value, _ ->
                        itemBinding.dosageValueTextView.text = getString(R.string.dosage_format, value)
                    }

                    ArrayAdapter.createFromResource(requireContext(), R.array.medication_frequency_options, android.R.layout.simple_spinner_item).also {
                        it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        itemBinding.frequencySpinner.adapter = it
                    }
                    itemBinding.frequencySpinner.onItemSelectedListener = this

                    val slotOptions = (1..8).map { getString(R.string.slot_n, it) }.toTypedArray()
                    val slotAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, slotOptions)
                    slotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    itemBinding.slotSpinner.adapter = slotAdapter

                    while (occupiedSlots.contains(nextAvailableSlot)) { nextAvailableSlot++ }
                    if (nextAvailableSlot <= 8) {
                        itemBinding.slotSpinner.setSelection(nextAvailableSlot - 1)
                        nextAvailableSlot++
                    }
                    binding.medicationInputContainer.addView(itemBinding.root)
                }
            }
            R.id.frequencySpinner -> updateTimeSettingsVisibility(position)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateQuantitySpinner() {
        val occupiedSlots = viewModel.medicationList.value?.size ?: 0
        val availableSlots = 8 - occupiedSlots
        val quantityOptions = (1..availableSlots).map { it.toString() }.toTypedArray()
        val quantityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, quantityOptions)
        quantityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.quantitySpinner.adapter = quantityAdapter
    }

    private fun updateMedication() {
        val medicationViews = (0 until binding.medicationInputContainer.childCount).map {
            binding.medicationInputContainer.getChildAt(it)
        }
        if (medicationViews.isEmpty()) {
            clearInputFields()
            return
        }
        val itemBinding = MedicationInputItemBinding.bind(medicationViews[0])
        val originalName = itemBinding.medicationNameEditText.tag as? String ?: return
        val originalMedication = viewModel.medicationList.value?.find { it.name == originalName } ?: return

        (activity as? MainActivity)?.deleteMedication(originalMedication, showToast = false)

        val name = itemBinding.medicationNameEditText.text.toString()
        val dosage = itemBinding.dosageValueTextView.text.toString()
        val frequency = itemBinding.frequencySpinner.selectedItem.toString()
        val slot = itemBinding.slotSpinner.selectedItemPosition + 1

        if (name.isBlank() || viewModel.startDate.value == null || viewModel.endDate.value == null) {
            Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.addMedication(originalMedication)
            return
        }
        if (viewModel.startDate.value!!.after(viewModel.endDate.value)) {
            Toast.makeText(requireContext(), getString(R.string.start_date_after_end_date), Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.addMedication(originalMedication)
            return
        }
        val requiredTimes = when (itemBinding.frequencySpinner.selectedItemPosition) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 0 }
        if (requiredTimes > 0 && viewModel.selectedTimes.value?.size != requiredTimes) {
            Toast.makeText(requireContext(), getString(R.string.set_all_medication_times), Toast.LENGTH_SHORT).show()
            (activity as? MainActivity)?.addMedication(originalMedication)
            return
        }

        val diff = viewModel.endDate.value!!.timeInMillis - viewModel.startDate.value!!.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(diff) + 1
        val timesPerDay = itemBinding.frequencySpinner.selectedItemPosition + 1
        val dosagePerTime = itemBinding.dosageValueTextView.text.toString().split(" ").first().toFloatOrNull() ?: 1.0f
        val totalPills = (days * timesPerDay * dosagePerTime).roundToInt()

        val isSlotOccupied = viewModel.medicationList.value?.any { it.slotNumber == slot && it.name != originalName } ?: false
        if (isSlotOccupied) {
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.slot_occupied_title)).setMessage(getString(R.string.slot_occupied_message, slot)).setPositiveButton(R.string.ok, null).show()
            (activity as? MainActivity)?.addMedication(originalMedication)
            return
        }

        val updatedMedication = Medication(
            name = name, dosage = dosage, frequency = frequency, startDate = viewModel.startDate.value!!.timeInMillis,
            endDate = viewModel.endDate.value!!.timeInMillis, times = viewModel.selectedTimes.value!!.mapValues { it.value.timeInMillis },
            id = MainActivity.generateNotificationId(), slotNumber = slot, totalPills = totalPills, remainingPills = totalPills
        )

        (activity as? MainActivity)?.addMedication(updatedMedication)
        clearInputFields()
        Toast.makeText(requireContext(), getString(R.string.medication_updated, name), Toast.LENGTH_SHORT).show()
    }
}
