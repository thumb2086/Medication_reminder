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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.MainActivity
import com.example.medicationreminderapp.Medication
import com.example.medicationreminderapp.R
import com.example.medicationreminderapp.databinding.FragmentReminderBinding
import java.text.SimpleDateFormat
import java.util.*

class ReminderFragment : Fragment(), AdapterView.OnItemSelectedListener {

    private var _binding: FragmentReminderBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

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
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.medication_frequency_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.frequencySpinner.adapter = adapter
        }
        binding.frequencySpinner.onItemSelectedListener = this

        val slotOptions = (1..8).map { getString(R.string.slot_n, it) }.toTypedArray()
        val slotAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, slotOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.slotSpinner.adapter = slotAdapter

        binding.medicationNameEditText.addTextChangedListener { text ->
            binding.notesEditText.setText(viewModel.notesMap.value?.get(text.toString()) ?: "")
        }

        binding.dosageSlider.addOnChangeListener { _, value, _ ->
            binding.dosageValueTextView.text = getString(R.string.dosage_format, value)
        }
        binding.dosageValueTextView.text = getString(R.string.dosage_format, binding.dosageSlider.value)

        binding.connectBoxButton.setOnClickListener {
            if (viewModel.isBleConnected.value == true) {
                (activity as? MainActivity)?.requestBluetoothPermissionsAndScan()
            } else {
                (activity as? MainActivity)?.requestBluetoothPermissionsAndScan()
            }
        }

        binding.addMedicationButton.setOnClickListener { addMedication() }
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

        binding.showAllMedicationsButton.setOnLongClickListener {
            showMedicationListForDeletion()
            true
        }
    }

    private fun observeViewModel() {
        viewModel.bleStatus.observe(viewLifecycleOwner) {
            binding.bleStatusTextView.text = getString(R.string.ble_status, it)
        }
        viewModel.isBleConnected.observe(viewLifecycleOwner) {
            binding.connectBoxButton.text = if (it) getString(R.string.disconnect_from_box) else getString(R.string.connect_to_box)
            binding.testCommandButton.isVisible = it
        }
    }

    private fun addMedication() {
        val name = binding.medicationNameEditText.text.toString()
        val dosage = binding.dosageValueTextView.text.toString()
        val totalPills = binding.totalPillsEditText.text.toString().toIntOrNull() ?: 0
        val frequency = binding.frequencySpinner.selectedItem.toString()
        val slot = binding.slotSpinner.selectedItemPosition + 1

        if (name.isBlank() || viewModel.startDate.value == null || viewModel.endDate.value == null) {
            Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (viewModel.startDate.value!!.after(viewModel.endDate.value)) {
            Toast.makeText(requireContext(), getString(R.string.start_date_after_end_date), Toast.LENGTH_SHORT).show()
            return
        }

        val requiredTimes = when (binding.frequencySpinner.selectedItemPosition) {
            0 -> 1
            1 -> 2
            2 -> 3
            3 -> 4
            else -> 0
        }
        if (requiredTimes > 0 && viewModel.selectedTimes.value?.size != requiredTimes) {
            Toast.makeText(requireContext(), getString(R.string.set_all_medication_times), Toast.LENGTH_SHORT).show()
            return
        }

        val isSlotOccupied = viewModel.medicationList.value?.any { it.slotNumber == slot } ?: false
        if (isSlotOccupied) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.slot_occupied_title))
                .setMessage(getString(R.string.slot_occupied_message, slot))
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val newMedication = Medication(
            name = name,
            dosage = dosage,
            frequency = frequency,
            startDate = viewModel.startDate.value!!.timeInMillis,
            endDate = viewModel.endDate.value!!.timeInMillis,
            times = viewModel.selectedTimes.value!!.mapValues { it.value.timeInMillis },
            id = MainActivity.generateNotificationId(),
            slotNumber = slot,
            totalPills = totalPills,
            remainingPills = totalPills
        )

        (activity as? MainActivity)?.addMedication(newMedication)
        clearInputFields()
    }

    private fun showDatePickerDialog(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
            val fmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(sel.time)
            if (isStart) {
                viewModel.startDate.value = sel
                binding.startDateButton.text = fmt
            } else {
                viewModel.endDate.value = sel
                binding.endDateButton.text = fmt
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
            viewModel.selectedTimes.value = mutableMapOf()
            updateSelectedTimesDisplay()
        }
    }

    private fun showAllMedicationsInTextView() {
        val medList = viewModel.medicationList.value ?: emptyList()
        if (medList.isEmpty()) {
            binding.displayNotesTextView.text = getString(R.string.no_medication_reminders)
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
        medList.forEachIndexed { index, med ->
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
            viewModel.notesMap.value?.get(med.name)?.takeIf { it.isNotBlank() }?.let { builder.append(getString(R.string.medication_info_notes, it)) }
            builder.append("\n\n")
        }
        binding.displayNotesTextView.text = builder.toString()
    }

    private fun showMedicationListForDeletion() {
        val medList = viewModel.medicationList.value ?: emptyList()
        if (medList.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_medication_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        val medNames = medList.map { "${it.name} (#${it.slotNumber})" }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_delete_title)).setItems(medNames) { _, which ->
            confirmAndDeleteMedication(medList[which])
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmAndDeleteMedication(medication: Medication) {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_delete_title)).setMessage(getString(R.string.confirm_delete_message, medication.name))
            .setPositiveButton(R.string.delete) { _, _ -> (activity as? MainActivity)?.deleteMedication(medication) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun clearInputFields() {
        binding.medicationNameEditText.setText("")
        binding.notesEditText.setText("")
        binding.totalPillsEditText.setText("")
        binding.frequencySpinner.setSelection(0)
        viewModel.startDate.value = null
        viewModel.endDate.value = null
        binding.startDateButton.text = getString(R.string.select_start_date)
        binding.endDateButton.text = getString(R.string.select_end_date)
        viewModel.selectedTimes.value = mutableMapOf()
        updateSelectedTimesDisplay()
        binding.dosageSlider.value = 1.0f
        binding.dosageValueTextView.text = getString(R.string.dosage_format, binding.dosageSlider.value)
        binding.slotSpinner.setSelection(0)
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (parent?.id == R.id.frequencySpinner) {
            updateTimeSettingsVisibility(position)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}