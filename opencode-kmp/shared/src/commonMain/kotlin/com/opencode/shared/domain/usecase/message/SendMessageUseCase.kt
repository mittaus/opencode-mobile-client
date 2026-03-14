package com.opencode.shared.domain.usecase.message

import com.opencode.shared.domain.model.Message
import com.opencode.shared.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

class SendMessageUseCase(private val repo: MessageRepository) {
    operator fun invoke(
        sessionId: String,
        text: String,
        modelId: String? = null,
        agent: String? = null,
    ): Flow<Message> = repo.sendMessage(sessionId, text, modelId, agent)
}
