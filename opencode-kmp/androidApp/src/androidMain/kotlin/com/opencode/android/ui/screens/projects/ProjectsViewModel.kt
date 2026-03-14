package com.opencode.android.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.di.setActiveProject
import com.opencode.shared.domain.repository.AvailableProject
import com.opencode.shared.domain.usecase.project.GetAvailableProjectsUseCase
import com.opencode.shared.domain.usecase.project.RegisterProjectUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<AvailableProject> = emptyList(),
    val activeWorktree: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRegistering: Boolean = false,
    val registerError: String? = null,
)

class ProjectsViewModel(
    private val getAvailableProjects: GetAvailableProjectsUseCase,
    private val registerProject: RegisterProjectUseCase,
    private val api: OpenCodeApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    // Emitted when a project is selected — tells the host to navigate to Sessions
    private val _navigateTo = MutableSharedFlow<Pair<String, String>>()  // worktree to projectId
    val navigateTo = _navigateTo.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getAvailableProjects()
                .onSuccess { projects ->
                    _state.update {
                        it.copy(projects = projects, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /**
     * Registers a new project at the given server-side directory path.
     * Ensures git is initialised, creates an OpenCode session for it, then refreshes the list.
     */
    fun registerProject(directory: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true, registerError = null) }
            val result = registerProject.invoke(directory)
            result
                .onSuccess { load() }
                .onFailure { e ->
                    _state.update { it.copy(isRegistering = false, registerError = e.message) }
                    return@launch
                }
            _state.update { it.copy(isRegistering = false) }
        }
    }

    /**
     * Called when the user taps a project card.
     *
     * For SESSION-type projects (no .git, projectId="global"), automatically registers the
     * directory first (git init + OpenCode project creation) so the AI operates in the
     * correct directory. After registration the project list is reloaded to obtain the real
     * projectId before navigating.
     *
     * For already-registered projects, navigates immediately.
     */
    fun selectProject(project: AvailableProject) {
        setActiveProject(project.worktree, api)
        _state.update { it.copy(activeWorktree = project.worktree) }

        // Navigate directly — do NOT auto-create a session stub for unregistered projects.
        // Stubs were being created on every project tap, leaving orphan "New session" entries.
        viewModelScope.launch { _navigateTo.emit(project.worktree to project.projectId) }
    }
}
