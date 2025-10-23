package com.example.medicationreminderapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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

    // List to hold bindings and state for each card
    private val medicationCards = mutableListOf<Pair<MedicationInputItemBinding, MedicationCardState>>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderSettingsBinding.inflate(inflater, container, false)
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
        // Observe changes in the medication list to update the available slots
        viewModel.medicationList.observe(viewLifecycleOwner) {
            setupMedicationCountSpinner()
            updateAllSlotSpinners() // In case a medication was deleted elsewhere
        }
    }

    private fun setupListeners() {
        binding.connectButton.setOnClickListener {
            (activity as? MainActivity)?.requestBluetoothPermissionsAndScan()
        }

        binding.addReminderButton.setOnClickListener {
            collectAndSaveMedications()
        }

        binding.medicationCountSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedCount = (position + 1)
            updateMedicationCards(selectedCount)
        }
    }

    private fun getAvailableSlots(): List<Int> {
        val allSlots = (1..8).toSet()
        val occupiedSlots = viewModel.medicationList.value?.map { it.slotNumber }?.toSet() ?: emptySet()
        return (allSlots - occupiedSlots).toList().sorted()
    }

    private fun setupMedicationCountSpinner() {
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
        // Clear previous cards
        binding.medicationCardsContainer.removeAllViews()
        medicationCards.clear()

        // Create new cards
        for (i in 1..count) {
            val cardBinding = MedicationInputItemBinding.inflate(layoutInflater, binding.medicationCardsContainer, false)
            val cardState = MedicationCardState()
            medicationCards.add(Pair(cardBinding, cardState))
            binding.medicationCardsContainer.addView(cardBinding.root)

            setupCard(cardBinding, cardState)
        }
        updateAllSlotSpinners()
    }

    private fun setupCard(cardBinding: MedicationInputItemBinding, cardState: MedicationCardState) {
        // Setup listeners for date and time pickers
        cardBinding.startDateEditText.setOnClickListener { showDatePickerDialog(cardBinding, cardState, isStartDate = true) }
        cardBinding.endDateEditText.setOnClickListener { showDatePickerDialog(cardBinding, cardState, isStartDate = false) }
        cardBinding.timePickerButton.setOnClickListener { showTimePickerDialog(cardBinding, cardState) }

        // Setup listener for the slot spinner
        cardBinding.slotNumberSpinner.setOnItemClickListener { _, _, position, _ ->
            val adapter = cardBinding.slotNumberSpinner.adapter
            cardState.selectedSlot = adapter.getItem(position) as Int
            updateAllSlotSpinners() // Update other spinners to exclude the newly selected slot
        }

        // Initialize dosage label
        cardBinding.dosageLabelTextView.text = getString(R.string.dosage_pills, cardBinding.dosageSlider.value.toInt())
        cardBinding.dosageSlider.addOnChangeListener { _, value, _ ->
            cardBinding.dosageLabelTextView.text = getString(R.string.dosage_pills, value.toInt())
        }
    }

    private fun updateAllSlotSpinners() {
        val globallyAvailableSlots = getAvailableSlots()
        val selectedInThisForm = medicationCards.mapNotNull { it.second.selectedSlot }

        medicationCards.forEach { (cardBinding, cardState) ->
            // A slot is available for this card if it's globally available AND not selected by another card in this form
            // OR if it's the one currently selected by this card itself.
            val availableForThisCard = globallyAvailableSlots.filter { slot ->
                !selectedInThisForm.contains(slot) || slot == cardState.selectedSlot
            }.sorted()

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, availableForThisCard)
            cardBinding.slotNumberSpinner.setAdapter(adapter)

            // If a slot was previously selected, ensure it's still displayed
            cardState.selectedSlot?.let {
                 if (availableForThisCard.contains(it)) {
                    cardBinding.slotNumberSpinner.setText(it.toString(), false)
                 } else {
                     // The slot is no longer valid (e.g., deleted from another screen), so clear it
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
            id = View.generateViewId() // Unique ID for the chip
        }

        // Store time with chip's ID
        cardState.times[chip.id] = time

        chip.setOnCloseIconClickListener { view ->
            cardBinding.timeChipGroup.removeView(view)
            cardState.times.remove(view.id) // Remove time from state
        }
        cardBinding.timeChipGroup.addView(chip)
    }

    private fun collectAndSaveMedications() {
        val newMedications = mutableListOf<Medication>()
        var allFormsValid = true

        if (medicationCards.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.add_at_least_one_medication), Toast.LENGTH_SHORT).show()
            return
        }

        for ((cardBinding, cardState) in medicationCards) {
            val name = cardBinding.medicationNameEditText.text.toString()
            val slot = cardState.selectedSlot
            val startDate = cardState.startDate
            val endDate = cardState.endDate
            val times = cardState.times
            val dosage = cardBinding.dosageSlider.value
            val notes = cardBinding.notesEditText.text.toString()

            if (name.isBlank() || slot == null || startDate == null || endDate == null) {
                allFormsValid = false
                Toast.makeText(requireContext(), getString(R.string.please_fill_all_fields), Toast.LENGTH_LONG).show()
                break
            }

            if (times.isEmpty()) {
                allFormsValid = false
                Toast.makeText(requireContext(), getString(R.string.please_add_time), Toast.LENGTH_LONG).show()
                break
            }

            if (startDate.after(endDate)) {
                 allFormsValid = false
                Toast.makeText(requireContext(), getString(R.string.end_date_before_start_date), Toast.LENGTH_LONG).show()
                break
            }

            // Calculate total pills
            val diff = endDate.timeInMillis - startDate.timeInMillis
            val days = TimeUnit.MILLISECONDS.toDays(diff).toInt() + 1
            val dosesPerDay = times.size
            val pillsPerDose = dosage.toInt()
            val totalPills = days * dosesPerDay * pillsPerDose

            val timesMap = times.values.mapIndexed { index, calendar -> index to calendar.timeInMillis }.toMap()

            val newMed = Medication(
                id = Random.nextInt(),
                name = name,
                dosage = pillsPerDose.toString(),
                frequency = "", // Frequency is implicitly defined by the number of times
                startDate = startDate.timeInMillis,
                endDate = endDate.timeInMillis,
                times = timesMap,
                slotNumber = slot,
                totalPills = totalPills,
                remainingPills = totalPills
            )
            newMedications.add(newMed)

            // Also save notes to the shared ViewModel
             if (notes.isNotBlank()) {
                 viewModel.notesMap.value?.put(name, notes)
             }
        }

        if (allFormsValid) {
            viewModel.addMedications(newMedications)
            Toast.makeText(requireContext(), getString(R.string.medications_added_successfully), Toast.LENGTH_SHORT).show()
            // Reset the form
            binding.medicationCountSpinner.setText("", false)
            updateMedicationCards(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
