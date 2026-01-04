package com.example.medicationreminderapp.di

import android.content.Context
import com.example.medicationreminderapp.AppRepository
import com.example.medicationreminderapp.BluetoothLeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothLeManager(
        @ApplicationContext context: Context,
        repository: AppRepository
    ): BluetoothLeManager {
        return BluetoothLeManager(context, repository)
    }

    // AppRepository already has @Singleton and @Inject constructor,
    // so Hilt knows how to provide it. We don't need a @Provides function for it here.
    // Hilt will automatically inject MedicationDao and TakenRecordDao into it.
}
