package com.example.recallai.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.KnownPersonItem
import com.example.recallai.data.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PeopleBookUiState(
    val people: List<KnownPersonItem> = emptyList(),
    val nameInput: String = "",
    val relationInput: String = "",
    val noteInput: String = "",
    val phoneInput: String = "",
    val editingId: String? = null,
    val searchQuery: String = "",
    val info: String? = null
) {
    val filtered: List<KnownPersonItem>
        get() = if (searchQuery.isBlank()) people
        else people.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.relation.contains(searchQuery, ignoreCase = true) ||
                it.note.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery)
        }

    val isEditing: Boolean get() = editingId != null
}

@HiltViewModel
class PeopleMemoryBookViewModel @Inject constructor(
    private val toolkitRepository: CareToolkitRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PeopleBookUiState(people = toolkitRepository.getKnownPeople())
    )
    val uiState: StateFlow<PeopleBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            toolkitRepository.syncPeopleDirectoryFromServer()
            refreshPeopleFromLocal()
        }
    }

    private fun refreshPeopleFromLocal() {
        _uiState.update { it.copy(people = toolkitRepository.getKnownPeople()) }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(nameInput = v, info = null) }
    fun onRelationChange(v: String) = _uiState.update { it.copy(relationInput = v, info = null) }
    fun onNoteChange(v: String) = _uiState.update { it.copy(noteInput = v, info = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phoneInput = v, info = null) }
    fun onSearchChange(v: String) = _uiState.update { it.copy(searchQuery = v) }

    fun startEdit(person: KnownPersonItem) {
        _uiState.update {
            it.copy(
                editingId = person.id,
                nameInput = person.name,
                relationInput = person.relation,
                noteInput = person.note,
                phoneInput = person.phone,
                info = null
            )
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingId = null,
                nameInput = "",
                relationInput = "",
                noteInput = "",
                phoneInput = "",
                info = null
            )
        }
    }

    fun savePerson() {
        val s = _uiState.value
        val name = s.nameInput.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(info = "Please enter a name.") }
            return
        }
        val item = KnownPersonItem(
            id = s.editingId ?: UUID.randomUUID().toString(),
            name = name,
            relation = s.relationInput.trim(),
            note = s.noteInput.trim(),
            phone = s.phoneInput.trim(),
            updatedAt = System.currentTimeMillis()
        )
        val current = s.people.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        val updated = if (idx >= 0) {
            current.also { it[idx] = item }
        } else {
            listOf(item) + current
        }
        viewModelScope.launch {
            toolkitRepository.saveKnownPeople(updated)
            _uiState.update {
                it.copy(
                    people = updated,
                    editingId = null,
                    nameInput = "",
                    relationInput = "",
                    noteInput = "",
                    phoneInput = "",
                    info = if (idx >= 0) "Person updated." else "Person added to directory."
                )
            }
            memoryRepository.saveTextMemory(
                text = "Known person ${if (idx >= 0) "updated" else "added"}: ${item.name}" +
                    (if (item.relation.isNotBlank()) ", ${item.relation}" else "") +
                    (if (item.note.isNotBlank()) ". ${item.note}" else ""),
                title = "People Directory",
                type = "PEOPLE_MEMORY",
                tags = listOf("people", "directory", "identity")
            )
        }
    }

    fun removePerson(id: String) {
        viewModelScope.launch {
            val updated = _uiState.value.people.filterNot { it.id == id }
            toolkitRepository.saveKnownPeople(updated)
            _uiState.update { it.copy(people = updated, info = "Person removed.") }
        }
    }
}
