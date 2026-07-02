package com.example.recallai.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.data.CareToolkitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.recallai.reminders.ReminderRepository
import com.example.recallai.reminders.ReminderScheduleHelper
import com.example.recallai.reminders.ReminderScheduler
import javax.inject.Inject

data class RemindersUiState(
    val upcoming: List<ReminderEntity> = emptyList(),
    val completed: List<ReminderEntity> = emptyList()
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val careToolkitRepository: CareToolkitRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            careToolkitRepository.syncPatientToolkitFromServer()
        }
    }

    val uiState: StateFlow<RemindersUiState> = repo.remindersFlow
        .map { list ->
            val now = System.currentTimeMillis()
            val pending = list.filter { it.status == ReminderStatus.PENDING }.sortedBy { it.datetime }
            val completed = list.filter { it.status == ReminderStatus.COMPLETED }.sortedByDescending { it.datetime }
            // keep pending future first; overdue still show at top
            val orderedPending = pending.sortedWith(compareBy<ReminderEntity> { it.datetime < now }.thenBy { it.datetime })
            RemindersUiState(
                upcoming = orderedPending,
                completed = completed.take(40)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun toggleDone(reminder: ReminderEntity) {
        viewModelScope.launch {
            if (reminder.status == ReminderStatus.COMPLETED) {
                repo.markPending(reminder.id)
                val fresh = repo.getById(reminder.id) ?: return@launch
                var r = fresh
                val now = System.currentTimeMillis()
                if (r.repeatMode != ReminderRepeatMode.NONE && r.datetime <= now) {
                    val n = ReminderScheduleHelper.computeNextTrigger(r, now - 1)
                    if (n != null) {
                        r = r.copy(datetime = n)
                        repo.update(r)
                    }
                }
                ReminderScheduler.schedule(repo.context(), r)
            } else {
                repo.markDone(reminder.id)
                ReminderScheduler.cancel(repo.context(), reminder.id)
            }
        }
    }

    fun delete(reminder: ReminderEntity) {
        viewModelScope.launch {
            repo.delete(reminder)
            ReminderScheduler.cancel(repo.context(), reminder.id)
        }
    }
}

