package com.example.medicationreminderapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MedicationEntity::class, TakenRecordEntity::class], version = 1, exportSchema = false)
@TypeConverters(com.example.medicationreminderapp.data.database.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun takenRecordDao(): TakenRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medication_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
