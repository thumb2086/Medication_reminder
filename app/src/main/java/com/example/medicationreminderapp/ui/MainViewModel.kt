package com.example.medicationreminderapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.medicationreminderapp.Medication
import com.github.mikephil.charting.data.Entry
import java.util.*

class MainViewModel : ViewModel() {
    val medicationList = MutableLiveData<List<Medication>> (emptyList())
    val notesMap = MutableLiveData<Map<String, String>>(emptyMap())
    val dailyStatusMap = MutableLiveData<Map<String, Int>>(emptyMap())
    val bleStatus = MutableLiveData<String>()
    val isBleConnected = MutableLiveData<Boolean>()
    val temperature = MutableLiveData<Float>()
    val humidity = MutableLiveData<Float>()
    val complianceRate = MutableLiveData<Int>()

    val tempHistory = MutableLiveData<MutableList<Entry>>(mutableListOf())
    val humidityHistory = MutableLiveData<MutableList<Entry>>(mutableListOf())

    val startDate = MutableLiveData<Calendar?>()
    val endDate = MutableLiveData<Calendar?>()
    val selectedTimes = MutableLiveData<MutableMap<Int, Calendar>>(mutableMapOf())

    // For Guided Pill Filling
    private val _guidedFillConfirmation = MutableLiveData<Boolean>()
    val guidedFillConfirmation: LiveData<Boolean> = _guidedFillConfirmation

    fun onGuidedFillConfirmed() {
        _guidedFillConfirmation.value = true
    }

    fun onGuidedFillConfirmationConsumed() {
        _guidedFillConfirmation.value = false
    }


    fun updateComplianceRate(medications: List<Medication>, dailyStatus: Map<String, Int>) {
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        var totalExpected = 0
        var totalTaken = 0

        for (i in 0..29) {
            val day = (thirtyDaysAgo.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayStr = android.text.format.DateFormat.format("yyyy-MM-dd", day) as String

            var dailyExpectedCount = 0
            medications.forEach { med ->
                if (day.timeInMillis >= med.startDate && day.timeInMillis <= med.endDate) {
                    dailyExpectedCount += med.times.size
                }
            }

            if (dailyExpectedCount > 0) {
                totalExpected += 1
                if (dailyStatus[dayStr] == 2) { // STATUS_ALL_TAKEN
                    totalTaken += 1
                }
            }
        }

        complianceRate.value = if (totalExpected == 0) 100 else (totalTaken * 100 / totalExpected)
    }
}