package com.opencode.shared.domain.usecase.stats

import com.opencode.shared.domain.model.UsageStats
import com.opencode.shared.domain.repository.StatsRepository

class GetStatsUseCase(private val repo: StatsRepository) {
    suspend operator fun invoke(days: Int? = null): Result<UsageStats> = repo.getStats(days)
}
