package com.example.medicationreminderapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TakenRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTakenRecord(record: TakenRecordEntity)

    @Query("SELECT * FROM taken_records WHERE medication_id = :medicationId")
    fun getRecordsForMedication(medicationId: Int): Flow<List<TakenRecordEntity>>

    @Query("SELECT * FROM taken_records WHERE taken_timestamp >= :startDate AND taken_timestamp < :endDate")
    suspend fun getRecordsForDateRange(startDate: Long, endDate: Long): List<TakenRecordEntity>
}
