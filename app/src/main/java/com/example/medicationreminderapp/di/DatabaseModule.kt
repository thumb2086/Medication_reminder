package com.example.medicationreminderapp.di

import android.content.Context
import com.example.medicationreminderapp.data.database.AppDatabase
import com.example.medicationreminderapp.data.database.MedicationDao
import com.example.medicationreminderapp.data.database.TakenRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideMedicationDao(appDatabase: AppDatabase): MedicationDao {
        return appDatabase.medicationDao()
    }

    @Provides
    fun provideTakenRecordDao(appDatabase: AppDatabase): TakenRecordDao {
        return appDatabase.takenRecordDao()
    }
}
