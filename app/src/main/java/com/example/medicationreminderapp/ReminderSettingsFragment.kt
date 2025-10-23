package com.example.medicationreminderapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.medicationreminderapp.databinding.FragmentReminderSettingsBinding
import com.example.medicationreminderapp.databinding.MedicationInputItemBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Data class to hold the state for each dynamically created medication card
data class MedicationCardState(
    var startDate: Calendar? = null,
    var endDate: Calendar? = null,
    val times: MutableMap<Int, Calendar> = mutableMapOf(), // Map of chip ID to time
    var selectedSlot: Int? = null
)

class ReminderSettingsFragment : Fragment() {

    private var _binding: FragmentReminderSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var editingMedication: Medication? = null

    private val medicationCards = mutableListOf<Pair<MedicationInputItemBinding, MedicationCardState>>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private lateinit var alarmScheduler: AlarmScheduler

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderSettingsBinding.inflate(inflater, container, false)
        alarmScheduler = AlarmScheduler(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.bleStatus.observe(viewLifecycleOwner) { status ->
            binding.bleStatusTextView.text = status
        }
        viewModel.medicationList.observe(viewLifecycleOwner) {
            setupMedicationCountSpinner()
            updateAllSlotSpinners()
        }
    }

    private fun setupListeners() {
        binding.connectButton.setOnClickListener {
            (activity as? MainActivity)?.requestBluetoothPermissionsAndScan()
        }

        binding.addReminderButton.setOnClickListener {
            collectAndSaveMedications()
        }

        binding.editReminderButton.setOnClickListener {
            showMedicationSelectionDialog(isForEdit = true)
        }

        binding.deleteReminderButton.setOnClickListener {
            showMedicationSelectionDialog(isForEdit = false)
        }

        binding.medicationCountSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedCount = (position + 1)
            updateMedicationCards(selectedCount)
            editingMedication = null
            binding.addReminderButton.text = getString(R.string.add_medication_reminder)
        }
    }

    private fun showMedicationSelectionDialog(isForEdit: Boolean) {
        val medications = viewModel.medicationList.value ?: emptyList()
        if (medications.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_medication_to_modify), Toast.LENGTH_SHORT).show()
            return
        }

        val medicationNames = medications.map { it.name }.toTypedArray()
        val title = if (isForEdit) getString(R.string.select_medication_to_edit) else getString(R.string.select_medication_to_delete)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(medicationNames) { _, which ->
                val selectedMed = medications[which]
                if (isForEdit) {
                    populateFormForEdit(selectedMed)
                } else {
                    confirmAndDelete(selectedMed)
                }
            }
            .show()
    }

    private fun populateFormForEdit(med: Medication) {
        editingMedication = med
        updateMedicationCards(1)

        val (cardBinding, cardState) = medicationCards.first()

        cardBinding.medicationNameEditText.setText(med.name)
        cardBinding.dosageSlider.value = med.dosage.toFloat()

        cardState.startDate = Calendar.getInstance().apply { timeInMillis = med.startDate }
        cardState.endDate = Calendar.getInstance().apply { timeInMillis = med.endDate }
        cardBinding.startDateEditText.setText(dateFormat.format(cardState.startDate!!.time))
        cardBinding.endDateEditText.setText(dateFormat.format(cardState.endDate!!.time))

        cardState.selectedSlot = med.slotNumber
        updateAllSlotSpinners()

        cardBinding.timeChipGroup.removeAllViews()
        cardState.times.clear()
        med.times.values.forEach { timeInMillis ->
            val time = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
            addTimeChip(cardBinding, cardState, time)
        }

        binding.addReminderButton.text = getString(R.string.update_medication)
        binding.medicationCountSpinner.setText("1", false)
        binding.medicationCountSpinner.isEnabled = false
    }

    private fun confirmAndDelete(med: Medication) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message, med.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                alarmScheduler.cancel(med)
                viewModel.deleteMedication(med)
                Toast.makeText(requireContext(), getString(R.string.medication_deleted, med.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getAvailableSlots(): List<Int> {
        val allSlots = (1..8).toSet()
        val occupiedSlots = viewModel.medicationList.value
            ?.map { it.slotNumber }
            ?.filter { it != editingMedication?.slotNumber }
            ?.toSet() ?: emptySet()
        return (allSlots - occupiedSlots).toList().sorted()
    }

    private fun setupMedicationCountSpinner() {
        if (editingMedication != null) return

        val availableSlotCount = getAvailableSlots().size
        if (availableSlotCount == 0) {
            binding.medicationCountSpinner.setText(getString(R.string.no_available_slots), false)
            binding.medicationCountSpinner.isEnabled = false
            return
        }
        binding.medicationCountSpinner.isEnabled = true
        val countOptions = (1..availableSlotCount).map { it.toString() }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, countOptions)
        binding.medicationCountSpinner.setAdapter(adapter)
    }

    private fun updateMedicationCards(count: Int) {
        binding.medicationCardsContainer.removeAllViews()
        medicationCards.clear()

        repeat(count) {
            val cardBinding = MedicationInputItemBinding.inflate(layoutInflater, binding.medicationCardsContainer, false)
            val cardState = MedicationCardState()
            medicationCards.add(Pair(cardBinding, cardState))
            binding.medicationCardsContainer.addView(cardBinding.root)

            setupCard(cardBinding, cardState)
        }
        updateAllSlotSpinners()
    }

    private fun setupCard(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState) {
        cardBinding.startDateEditText.setOnClickListener { showDatePickerDialog(cardBinding, cardState, isStartDate = true) }
        cardBinding.endDateEditText.setOnClickListener { showDatePickerDialog(cardBinding, cardState, isStartDate = false) }
        cardBinding.timePickerButton.setOnClickListener { showTimePickerDialog(cardBinding, cardState) }

        cardBinding.slotNumberSpinner.setOnItemClickListener { _, _, position, _ ->
            val adapter = cardBinding.slotNumberSpinner.adapter
            val selected = adapter?.getItem(position) as? Int
            if (selected != null) {
                cardState.selectedSlot = selected
                updateAllSlotSpinners()
            }
        }

        cardBinding.dosageLabelTextView.text = getString(R.string.dosage_pills, cardBinding.dosageSlider.value.toInt())
        cardBinding.dosageSlider.addOnChangeListener { _, value, _ ->
            cardBinding.dosageLabelTextView.text = getString(R.string.dosage_pills, value.toInt())
        }
    }

    private fun updateAllSlotSpinners() {
        val globallyAvailableSlots = getAvailableSlots()
        val selectedInThisForm = medicationCards.mapNotNull { it.second.selectedSlot }

        medicationCards.forEach { (cardBinding, cardState) ->
            val availableForThisCard = globallyAvailableSlots.filter {
                !selectedInThisForm.contains(it) || it == cardState.selectedSlot
            }.sorted()

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, availableForThisCard)
            cardBinding.slotNumberSpinner.setAdapter(adapter)

            cardState.selectedSlot?.let {
                if (availableForThisCard.contains(it)) {
                    cardBinding.slotNumberSpinner.setText(it.toString(), false)
                } else {
                    cardBinding.slotNumberSpinner.setText("", false)
                    cardState.selectedSlot = null
                }
            }
        }
    }

    private fun showDatePickerDialog(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState, isStartDate: Boolean) {
        val calendar = if (isStartDate) cardState.startDate else cardState.endDate
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth, 0, 0, 0) }
            val formattedDate = dateFormat.format(selectedDate.time)
            if (isStartDate) {
                cardState.startDate = selectedDate
                cardBinding.startDateEditText.setText(formattedDate)
            } else {
                cardState.endDate = selectedDate
                cardBinding.endDateEditText.setText(formattedDate)
            }
        }
        val currentCalendar = calendar ?: Calendar.getInstance()
        DatePickerDialog(
            requireContext(), dateSetListener,
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH),
            currentCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePickerDialog(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState) {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            val selectedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            addTimeChip(cardBinding, cardState, selectedTime)
        }
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(), timeSetListener,
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun addTimeChip(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState, time: Calendar) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val chip = Chip(context).apply {
            text = timeFormat.format(time.time)
            isCloseIconVisible = true
            id = View.generateViewId()
        }

        cardState.times[chip.id] = time

        chip.setOnCloseIconClickListener { view ->
            cardBinding.timeChipGroup.removeView(view)
            cardState.times.remove(view.id)
        }
        cardBinding.timeChipGroup.addView(chip)
    }

    private fun collectAndSaveMedications() {
        if (editingMedication != null) {
            collectAndUpdateSingleMedication()
        } else {
            collectAndAddNewMedications()
        }
    }

    private fun collectAndUpdateSingleMedication() {
        val (cardBinding, cardState) = medicationCards.firstOrNull() ?: return
        val medToUpdate = editingMedication ?: return

        // First, cancel the old alarms
        alarmScheduler.cancel(medToUpdate)

        val updatedMed = createMedicationFromInput(cardBinding, cardState, medToUpdate.id)
        if (updatedMed != null) {
            viewModel.updateMedication(updatedMed)
            alarmScheduler.schedule(updatedMed) // Schedule new alarms
            Toast.makeText(requireContext(), getString(R.string.medication_updated, updatedMed.name), Toast.LENGTH_SHORT).show()
            resetForm()
        }
    }

    private fun collectAndAddNewMedications() {
        val newMedications = mutableListOf<Medication>()
        var allFormsValid = true

        if (medicationCards.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.add_at_least_one_medication), Toast.LENGTH_SHORT).show()
            return
        }

        for ((cardBinding, cardState) in medicationCards) {
            val newMed = createMedicationFromInput(cardBinding, cardState, Random.nextInt())
            if (newMed == null) {
                allFormsValid = false
                break
            }
            newMedications.add(newMed)
        }

        if (allFormsValid) {
            viewModel.addMedications(newMedications)
            newMedications.forEach { alarmScheduler.schedule(it) }
            Toast.makeText(requireContext(), getString(R.string.medications_added_successfully), Toast.LENGTH_SHORT).show()
            resetForm()
        }
    }

    private fun createMedicationFromInput(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState, medicationId: Int): Medication? {
        val name = cardBinding.medicationNameEditText.text.toString()
        val slot = cardState.selectedSlot
        val startDate = cardState.startDate
        val endDate = cardState.endDate
        val times = cardState.times
        val dosage = cardBinding.dosageSlider.value
        val notes = cardBinding.notesEditText.text.toString()

        if (name.isBlank() || slot == null || startDate == null || endDate == null) {
            Toast.makeText(requireContext(), getString(R.string.please_fill_all_fields), Toast.LENGTH_LONG).show()
            return null
        }

        if (times.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.please_add_time), Toast.LENGTH_LONG).show()
            return null
        }

        if (startDate.after(endDate)) {
            Toast.makeText(requireContext(), getString(R.string.end_date_before_start_date), Toast.LENGTH_LONG).show()
            return null
        }

        val diff = endDate.timeInMillis - startDate.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(diff).toInt() + 1
        val dosesPerDay = times.size
        val pillsPerDose = dosage.toInt()
        val totalPills = days * dosesPerDay * pillsPerDose

        val timesMap = times.values.mapIndexed { index, calendar -> index to calendar.timeInMillis }.toMap()

        if (notes.isNotBlank()) {
            viewModel.notesMap.value?.put(name, notes)
        }

        return Medication(
            id = medicationId,
            name = name,
            dosage = pillsPerDose.toString(),
            frequency = "",
            startDate = startDate.timeInMillis,
            endDate = endDate.timeInMillis,
            times = timesMap,
            slotNumber = slot,
            totalPills = totalPills,
            remainingPills = totalPills
        )
    }

    private fun resetForm() {
        editingMedication = null
        binding.medicationCountSpinner.setText("", false)
        binding.medicationCountSpinner.isEnabled = true
        updateMedicationCards(0)
        binding.addReminderButton.text = getString(R.string.add_medication_reminder)
        setupMedicationCountSpinner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
