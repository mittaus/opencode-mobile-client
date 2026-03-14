package com.opencode.shared.domain.model

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val parts: List<MessagePart>,
    val createdAt: Long,
    val tokens: TokenUsage? = null,
)

enum class MessageRole { USER, ASSISTANT }

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
)

sealed class MessagePart {
    data class Text(val text: String) : MessagePart()
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val input: Map<String, String>,
        val state: ToolState,
    ) : MessagePart()
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val output: String,
        val isError: Boolean = false,
    ) : MessagePart()
    data class StepStart(val text: String) : MessagePart()
    data class Image(val base64: String, val mimeType: String) : MessagePart()
}

enum class ToolState { PENDING, RUNNING, COMPLETED, ERROR }
