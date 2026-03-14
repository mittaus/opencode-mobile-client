package com.opencode.android.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.domain.model.Session
import com.opencode.shared.domain.usecase.session.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingSession: Boolean = false,
    val error: String? = null,
)

class SessionsViewModel(
    private val projectPath: String,
    private val projectId: String,
    private val getSessions: GetSessionsUseCase,
    private val createSession: CreateSessionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()
    private val _openChat = MutableSharedFlow<String>()
    val openChat = _openChat.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getSessions(project = projectPath).fold(
                onSuccess = { list -> _state.update { it.copy(sessions = list, isLoading = false) } },
                onFailure = { e -> _state.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun newSession() {
        if (_state.value.isCreatingSession) return
        viewModelScope.launch {
            _state.update { it.copy(isCreatingSession = true) }
            createSession(projectId, directory = projectPath)
                .onSuccess { _openChat.emit(it.id) }
            _state.update { it.copy(isCreatingSession = false) }
        }
    }
}
