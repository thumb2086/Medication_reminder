package com.example.medicationreminderapp

data class Medication(
    val name: String,
    val dosage: String,
    val startDate: Long,
    val endDate: Long,
    val times: Map<Int, Long>,
    val id: Int,
    val slotNumber: Int,
    var totalPills: Int,
    var remainingPills: Int
)
