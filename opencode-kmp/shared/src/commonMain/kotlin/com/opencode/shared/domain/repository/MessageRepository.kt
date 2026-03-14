package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessages(sessionId: String): Result<List<Message>>
    fun sendMessage(
        sessionId: String,
        text: String,
        modelId: String? = null,
        providerId: String? = null,
        agent: String? = null,
        attachments: List<String> = emptyList(),
    ): Flow<Message>
    suspend fun sendMessageAsync(
        sessionId: String,
        text: String,
        modelId: String? = null,
        providerId: String? = null,
        agent: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
    ): Result<Unit>
    suspend fun executeCommand(
        sessionId: String,
        command: String,
        arguments: String = "",
        agent: String? = null,
    ): Result<Message>
}
