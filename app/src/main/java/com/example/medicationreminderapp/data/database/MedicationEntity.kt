package com.example.medicationreminderapp.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "dosage")
    val dosage: String,

    @ColumnInfo(name = "start_date")
    val startDate: Long,

    @ColumnInfo(name = "end_date")
    val endDate: Long,

    @ColumnInfo(name = "times")
    val times: String, // Storing Map as JSON String, will require a TypeConverter

    @ColumnInfo(name = "slot_number")
    val slotNumber: Int,

    @ColumnInfo(name = "total_pills")
    var totalPills: Int,

    @ColumnInfo(name = "remaining_pills")
    var remainingPills: Int
)
