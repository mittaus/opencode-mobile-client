package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.remote.mapper.toDomain
import com.opencode.shared.domain.model.Message
import com.opencode.shared.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MessageRepositoryImpl(private val api: OpenCodeApi) : MessageRepository {

    override suspend fun getMessages(sessionId: String): Result<List<Message>> = runCatching {
        api.getMessages(sessionId).map { it.toDomain() }
    }

    // sendMessage streams via SSE; here we post async and let the SSE flow deliver updates.
    // This flow emits the final completed message when the server returns.
    override fun sendMessage(
        sessionId: String,
        text: String,
        modelId: String?,
        providerId: String?,
        agent: String?,
        attachments: List<String>,
    ): Flow<Message> = flow {
        val result = api.sendMessage(sessionId, text, modelId, providerId, agent)
        emit(result.toDomain())
    }

    override suspend fun sendMessageAsync(
        sessionId: String,
        text: String,
        modelId: String?,
        providerId: String?,
        agent: String?,
        imageBase64: String?,
        imageMimeType: String?,
    ): Result<Unit> = runCatching {
        api.sendMessageAsync(sessionId, text, modelId, providerId, agent, imageBase64, imageMimeType)
    }

    override suspend fun executeCommand(
        sessionId: String,
        command: String,
        arguments: String,
        agent: String?,
    ): Result<Message> = runCatching {
        api.executeCommand(sessionId, command, arguments, agent).toDomain()
    }
}
