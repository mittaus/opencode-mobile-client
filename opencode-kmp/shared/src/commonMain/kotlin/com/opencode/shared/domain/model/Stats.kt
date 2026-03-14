package com.opencode.shared.domain.model

data class UsageStats(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val estimatedCostUsd: Double,
    val sessionCount: Int,
    val promptCount: Int,
    val usageByModel: List<ModelUsage>,
    val usageByTool: List<ToolUsage>,
)

data class ModelUsage(
    val modelId: String,
    val providerId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val estimatedCostUsd: Double,
    val percentage: Float,
)

data class ToolUsage(
    val toolName: String,
    val callCount: Int,
    val percentage: Float,
)
