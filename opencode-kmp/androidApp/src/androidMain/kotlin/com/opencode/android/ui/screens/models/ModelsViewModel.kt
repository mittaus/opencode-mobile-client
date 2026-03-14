package com.opencode.android.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.domain.model.Provider
import com.opencode.shared.domain.usecase.project.GetProjectsUseCase
import com.opencode.shared.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ModelsUiState(
    val providers: List<Provider> = emptyList(),
    val selectedModelId: String = "claude-sonnet-4-5",
    val selectedProviderId: String = "anthropic",
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

class ModelsViewModel(
    private val sessionId: String,
    private val currentModelId: String,
    private val providerRepo: ProviderRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ModelsUiState(selectedModelId = currentModelId.ifBlank { "claude-sonnet-4-5" })
    )
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            providerRepo.getProviders().onSuccess { providers ->
                // Show all providers that have models; connected ones appear first
                val withModels = providers.filter { it.models.isNotEmpty() }
                    .sortedByDescending { it.isConnected }
                _state.update { it.copy(providers = withModels, isLoading = false) }
            }.onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun onSearchChange(q: String) = _state.update { it.copy(searchQuery = q) }
    fun selectModel(modelId: String, providerId: String) =
        _state.update { it.copy(selectedModelId = modelId, selectedProviderId = providerId) }
}
