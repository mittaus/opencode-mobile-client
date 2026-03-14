package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MessageListItemDto(
    val info: MessageInfoDto,
    val parts: List<PartDto> = emptyList(),
)

@Serializable
data class MessageInfoDto(
    val id: String,
    @SerialName("sessionID") val sessionId: String = "",
    val role: String = "user",
    val time: TimeDto = TimeDto(),
    val tokens: TokenUsageDto? = null,
)

@Serializable
data class TokenUsageDto(
    @SerialName("input") val inputTokens: Int = 0,
    @SerialName("output") val outputTokens: Int = 0,
    @SerialName("cache_read") val cacheReadTokens: Int = 0,
    @SerialName("cache_creation") val cacheWriteTokens: Int = 0,
)

@Serializable
data class PartDto(
    val type: String,
    // text
    val text: String? = null,
    // tool_invocation
    @SerialName("toolInvocation") val toolInvocation: ToolInvocationDto? = null,
    // step_start
    val title: String? = null,
)

@Serializable
data class ToolInvocationDto(
    @SerialName("toolCallId") val toolCallId: String = "",
    @SerialName("toolName") val toolName: String = "",
    val args: JsonElement? = null,
    val state: String = "call",          // call | partial-call | result
    val result: JsonElement? = null,
    @SerialName("isError") val isError: Boolean = false,
)
