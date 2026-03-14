package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.*
import com.opencode.shared.domain.model.*
import kotlinx.serialization.json.*

fun MessageListItemDto.toDomain(): Message = Message(
    id = info.id,
    sessionId = info.sessionId,
    role = if (info.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
    parts = parts.mapNotNull { it.toDomain() },
    createdAt = info.time.created,
    tokens = info.tokens?.toDomain(),
)

fun PartDto.toDomain(): MessagePart? = when (type) {
    "text" -> text?.let { MessagePart.Text(it) }
    "tool-invocation", "tool_invocation" -> toolInvocation?.toDomain()
    "step-start", "step_start" -> MessagePart.StepStart(title ?: "")
    else -> null
}

fun ToolInvocationDto.toDomain(): MessagePart {
    val inputMap = try {
        args?.let {
            Json.decodeFromJsonElement<Map<String, JsonElement>>(it)
                .mapValues { e -> e.value.toString().trim('"') }
        } ?: emptyMap()
    } catch (e: Exception) { emptyMap() }

    return when (state) {
        "result" -> MessagePart.ToolResult(
            toolCallId = toolCallId,
            toolName = toolName,
            output = result?.toString()?.take(500) ?: "",
            isError = isError,
        )
        else -> MessagePart.ToolCall(
            toolCallId = toolCallId,
            toolName = toolName,
            input = inputMap,
            state = when (state) {
                "call" -> ToolState.RUNNING
                "partial-call" -> ToolState.PENDING
                else -> ToolState.PENDING
            },
        )
    }
}

fun TokenUsageDto.toDomain() = TokenUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheReadTokens = cacheReadTokens,
    cacheWriteTokens = cacheWriteTokens,
)
