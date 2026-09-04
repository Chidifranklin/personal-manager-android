package com.example.presentation.notes

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.NoteEntity
import com.example.data.repository.PersonalManagerRepository
import com.example.util.BiometricHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val unlockedNoteIds: Set<String> = emptySet(),
    val selectedNoteForView: NoteEntity? = null,
    val authErrorMessage: String? = null
)

class NotesViewModel(private val repository: PersonalManagerRepository) : ViewModel() {

    private val _unlockedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedNoteForView = MutableStateFlow<NoteEntity?>(null)
    private val _authErrorMessage = MutableStateFlow<String?>(null)

    val notesFlow: StateFlow<List<NoteEntity>> = repository.notesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unlockedNoteIds: StateFlow<Set<String>> = _unlockedNoteIds.asStateFlow()
    val selectedNoteForView: StateFlow<NoteEntity?> = _selectedNoteForView.asStateFlow()
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    fun createNote(title: String, content: String, isLocked: Boolean, category: String) {
        viewModelScope.launch {
            val note = repository.createNote(title, content, isLocked, category)
            if (isLocked) {
                // Keep unlocked for the authoring session
                _unlockedNoteIds.value = _unlockedNoteIds.value + note.noteId
            }
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            _unlockedNoteIds.value = _unlockedNoteIds.value - note.noteId
            if (_selectedNoteForView.value?.noteId == note.noteId) {
                _selectedNoteForView.value = null
            }
        }
    }

    fun openNote(activity: FragmentActivity, note: NoteEntity) {
        if (!note.isLocked || _unlockedNoteIds.value.contains(note.noteId)) {
            _selectedNoteForView.value = note
            _authErrorMessage.value = null
        } else {
            // Trigger Biometric / PIN prompt
            BiometricHelper.authenticate(
                activity = activity,
                title = "Unlock Private Note",
                subtitle = "Biometric authentication required to view \"${note.title}\"",
                onSuccess = {
                    _unlockedNoteIds.value = _unlockedNoteIds.value + note.noteId
                    _selectedNoteForView.value = note
                    _authErrorMessage.value = null
                },
                onError = { error ->
                    _authErrorMessage.value = error
                }
            )
        }
    }

    fun closeNoteDetail() {
        _selectedNoteForView.value = null
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }
}
