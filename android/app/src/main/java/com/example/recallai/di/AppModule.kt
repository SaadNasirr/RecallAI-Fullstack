package com.example.recallai.di

import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context
import com.example.recallai.data.CaregiverRulesRepository
import com.example.recallai.data.local.RecallDatabase
import com.example.recallai.data.local.MemoryDao
import com.example.recallai.data.local.MoodCheckInDao
import com.example.recallai.data.local.PatientAlarmDao
import com.example.recallai.data.local.ReminderDao
import com.example.recallai.data.local.SavedFaceDao
import com.example.recallai.reminders.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RecallDatabase =
        RecallDatabase.getInstance(context)
    @Provides
    @Singleton
    fun provideMemoryDao(db: RecallDatabase): MemoryDao = db.memoryDao()
    @Provides
    @Singleton
    fun provideReminderDao(db: RecallDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun providePatientAlarmDao(db: RecallDatabase): PatientAlarmDao = db.patientAlarmDao()

    @Provides
    @Singleton
    fun provideMoodCheckInDao(db: RecallDatabase): MoodCheckInDao = db.moodCheckInDao()

    @Provides
    @Singleton
    fun provideSavedFaceDao(db: RecallDatabase): SavedFaceDao = db.savedFaceDao()

    @Provides
    @Singleton
    fun provideReminderRepository(
        dao: ReminderDao,
        @ApplicationContext context: Context,
        careToolkitRepository: CareToolkitRepository
    ): ReminderRepository = ReminderRepository(dao, context, careToolkitRepository)

    @Provides
    @Singleton
    fun provideCaregiverRulesRepository(@ApplicationContext context: Context): CaregiverRulesRepository =
        CaregiverRulesRepository(context)

}