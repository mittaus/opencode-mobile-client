package com.opencode.android.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.domain.model.UsageStats
import com.opencode.shared.domain.usecase.stats.GetStatsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StatsUiState(
    val stats: UsageStats? = null,
    val selectedPeriod: Int? = 7,
    val isLoading: Boolean = true,
)

class StatsViewModel(private val getStats: GetStatsUseCase) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()
    init { load(7) }
    fun setPeriod(days: Int?) {
        _state.update { it.copy(selectedPeriod = days) }
        load(days)
    }
    private fun load(days: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getStats(days).onSuccess { stats -> _state.update { it.copy(stats = stats, isLoading = false) } }
                .onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }
}
