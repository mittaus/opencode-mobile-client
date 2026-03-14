package com.opencode.shared.data.repository

import com.opencode.shared.data.local.PreferencesStorage
import com.opencode.shared.domain.model.*
import com.opencode.shared.domain.repository.StatsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class StatsRepositoryImpl(private val storage: PreferencesStorage) : StatsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredStats(
        val sessions: MutableMap<String, SessionStats> = mutableMapOf(),
        val modelUsage: MutableMap<String, ModelStats> = mutableMapOf(),
        val toolCalls: MutableMap<String, Int> = mutableMapOf(),
    )

    @Serializable
    private data class SessionStats(
        val sessionId: String,
        val modelId: String,
        val providerId: String,
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val promptCount: Int = 0,
        val timestamp: Long = 0,
    )

    @Serializable
    private data class ModelStats(
        val modelId: String,
        val providerId: String,
        var inputTokens: Long = 0,
        var outputTokens: Long = 0,
    )

    // Model pricing per 1M tokens (USD) — fallback values
    private val defaultPricing = mapOf(
        "claude-sonnet-4-5" to Pair(3.0, 15.0),
        "claude-opus-4"     to Pair(15.0, 75.0),
        "claude-haiku-4-5"  to Pair(0.8, 4.0),
        "gemini-2.5-pro"    to Pair(1.25, 10.0),
        "gemini-2.5-flash"  to Pair(0.075, 0.3),
        "gpt-4o"            to Pair(2.5, 10.0),
        "o3-mini"           to Pair(1.1, 4.4),
    )

    private suspend fun loadStats(): StoredStats {
        val raw = storage.getString(KEY_STATS) ?: return StoredStats()
        return try { json.decodeFromString(raw) } catch (e: Exception) { StoredStats() }
    }

    private suspend fun saveStats(stats: StoredStats) {
        storage.putString(KEY_STATS, json.encodeToString(StoredStats.serializer(), stats))
    }

    override suspend fun getStats(days: Int?): Result<UsageStats> = runCatching {
        val stored = loadStats()
        val cutoff = if (days != null) {
            currentTimeMs() - days * 24 * 60 * 60 * 1000L
        } else 0L

        val filtered = stored.sessions.values.filter { it.timestamp >= cutoff }
        val totalInput = filtered.sumOf { it.inputTokens }
        val totalOutput = filtered.sumOf { it.outputTokens }

        val modelUsage = stored.modelUsage.values.map { m ->
            val (inPrice, outPrice) = defaultPricing[m.modelId] ?: Pair(3.0, 15.0)
            val cost = (m.inputTokens / 1_000_000.0 * inPrice) +
                       (m.outputTokens / 1_000_000.0 * outPrice)
            ModelUsage(
                modelId = m.modelId,
                providerId = m.providerId,
                inputTokens = m.inputTokens,
                outputTokens = m.outputTokens,
                estimatedCostUsd = cost,
                percentage = 0f, // calculated below
            )
        }.sortedByDescending { it.inputTokens + it.outputTokens }

        val totalTokens = modelUsage.sumOf { it.inputTokens + it.outputTokens }.toFloat()
        val modelUsageWithPct = modelUsage.map { m ->
            m.copy(percentage = if (totalTokens > 0) (m.inputTokens + m.outputTokens) / totalTokens else 0f)
        }

        val totalCost = modelUsageWithPct.sumOf { it.estimatedCostUsd }
        val totalToolCalls = stored.toolCalls.values.sum().toFloat()
        val toolUsage = stored.toolCalls.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                ToolUsage(
                    toolName = name,
                    callCount = count,
                    percentage = if (totalToolCalls > 0) count / totalToolCalls else 0f,
                )
            }

        UsageStats(
            totalInputTokens = totalInput,
            totalOutputTokens = totalOutput,
            estimatedCostUsd = totalCost,
            sessionCount = stored.sessions.size,
            promptCount = filtered.sumOf { it.promptCount },
            usageByModel = modelUsageWithPct,
            usageByTool = toolUsage,
        )
    }

    override suspend fun recordMessageUsage(
        sessionId: String,
        modelId: String,
        providerId: String,
        inputTokens: Int,
        outputTokens: Int,
    ) {
        val stored = loadStats()
        val existing = stored.sessions[sessionId] ?: SessionStats(
            sessionId, modelId, providerId, timestamp = currentTimeMs()
        )
        stored.sessions[sessionId] = existing.copy(
            inputTokens = existing.inputTokens + inputTokens,
            outputTokens = existing.outputTokens + outputTokens,
            promptCount = existing.promptCount + 1,
        )
        val mExisting = stored.modelUsage[modelId] ?: ModelStats(modelId, providerId)
        stored.modelUsage[modelId] = mExisting.copy(
            inputTokens = mExisting.inputTokens + inputTokens,
            outputTokens = mExisting.outputTokens + outputTokens,
        )
        saveStats(stored)
    }

    override suspend fun recordToolCall(toolName: String) {
        val stored = loadStats()
        stored.toolCalls[toolName] = (stored.toolCalls[toolName] ?: 0) + 1
        saveStats(stored)
    }

    companion object { const val KEY_STATS = "usage_stats" }
}

expect fun currentTimeMs(): Long
