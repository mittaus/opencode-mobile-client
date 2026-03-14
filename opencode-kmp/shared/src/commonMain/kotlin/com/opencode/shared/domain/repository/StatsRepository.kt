package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.UsageStats

interface StatsRepository {
    suspend fun getStats(days: Int? = null): Result<UsageStats>
    suspend fun recordMessageUsage(
        sessionId: String,
        modelId: String,
        providerId: String,
        inputTokens: Int,
        outputTokens: Int,
    )
    suspend fun recordToolCall(toolName: String)
}
