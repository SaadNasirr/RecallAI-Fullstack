package com.example.recallai.reminders

import android.content.Context
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.local.ReminderDao
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val appContext: Context,
    private val careToolkitRepository: CareToolkitRepository
) {
    val remindersFlow: Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun add(reminder: ReminderEntity): Long = withContext(Dispatchers.IO) {
        val stamped = reminder.copy(updatedAt = System.currentTimeMillis())
        val id = dao.upsert(stamped)
        careToolkitRepository.pushPatientToolkitToServer()
        id
    }

    suspend fun delete(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        dao.delete(reminder)
        careToolkitRepository.pushPatientToolkitToServer()
    }

    suspend fun update(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        dao.update(reminder.copy(updatedAt = System.currentTimeMillis()))
        careToolkitRepository.pushPatientToolkitToServer()
    }

    suspend fun markDone(id: Long) = withContext(Dispatchers.IO) {
        dao.setStatus(id = id, status = ReminderStatus.COMPLETED)
        careToolkitRepository.pushPatientToolkitToServer()
    }

    suspend fun markPending(id: Long) = withContext(Dispatchers.IO) {
        dao.setStatus(id = id, status = ReminderStatus.PENDING)
        careToolkitRepository.pushPatientToolkitToServer()
    }

    suspend fun getUpcomingPending(fromMs: Long = System.currentTimeMillis()): List<ReminderEntity> =
        withContext(Dispatchers.IO) { dao.getUpcomingPending(fromMs) }

    suspend fun getById(id: Long): ReminderEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    fun context(): Context = appContext
}
