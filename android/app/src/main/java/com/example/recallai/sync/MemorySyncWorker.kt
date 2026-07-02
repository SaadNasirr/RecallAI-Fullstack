package com.example.recallai.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.recallai.data.MemoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemorySyncEntryPoint {
    fun memoryRepository(): MemoryRepository
}

class MemorySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, MemorySyncEntryPoint::class.java)
            .memoryRepository()

        return try {
            val success = repo.syncFromServer()
            if (success) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
