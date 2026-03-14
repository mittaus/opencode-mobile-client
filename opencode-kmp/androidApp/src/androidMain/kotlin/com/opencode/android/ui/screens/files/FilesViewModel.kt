package com.opencode.android.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.usecase.project.CommitChangesUseCase
import com.opencode.shared.domain.usecase.project.DiscardChangesUseCase
import com.opencode.shared.domain.usecase.project.GetVcsDiffUseCase
import com.opencode.shared.domain.usecase.project.StageFilesUseCase
import com.opencode.shared.domain.usecase.project.UnstageFilesUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FilesUiState(
    val diffs: List<FileDiff> = emptyList(),
    val commitMessage: String = "",
    val isCommitting: Boolean = false,
    val selectedDiff: FileDiff? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val staged: List<FileDiff> get() = diffs.filter { it.staged }
    val unstaged: List<FileDiff> get() = diffs.filter { !it.staged }
}

class FilesViewModel(
    private val getVcsDiff: GetVcsDiffUseCase,
    private val stageFiles: StageFilesUseCase,
    private val unstageFiles: UnstageFilesUseCase,
    private val discardChanges: DiscardChangesUseCase,
    private val commitChanges: CommitChangesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getVcsDiff().fold(
                onSuccess = { list -> _state.update { it.copy(diffs = list, isLoading = false) } },
                onFailure = { e -> _state.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun openDiff(diff: FileDiff) = _state.update { it.copy(selectedDiff = diff) }
    fun closeDiff() = _state.update { it.copy(selectedDiff = null) }

    fun onCommitMessageChange(msg: String) = _state.update { it.copy(commitMessage = msg) }

    fun stage(path: String) {
        viewModelScope.launch {
            stageFiles(listOf(path)).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    fun unstage(path: String) {
        viewModelScope.launch {
            unstageFiles(listOf(path)).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    fun discard(path: String, status: String) {
        viewModelScope.launch {
            discardChanges(path, status).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    fun commit() {
        val message = _state.value.commitMessage.trim()
        if (message.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isCommitting = true, error = null) }
            val result = if (_state.value.staged.isEmpty()) {
                // Nada en stage → stage everything and then commit
                val allPaths = _state.value.diffs.map { it.path }
                stageFiles(allPaths).fold(
                    onSuccess = { commitChanges(message) },
                    onFailure = { Result.failure(it) },
                )
            } else {
                commitChanges(message)
            }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isCommitting = false, commitMessage = "") }
                    load()
                },
                onFailure = { e ->
                    _state.update { it.copy(isCommitting = false, error = e.message) }
                },
            )
        }
    }
}
