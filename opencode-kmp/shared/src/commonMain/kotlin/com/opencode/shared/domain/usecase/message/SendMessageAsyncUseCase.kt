package com.opencode.shared.domain.usecase.message

import com.opencode.shared.domain.repository.MessageRepository

class SendMessageAsyncUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        modelId: String? = null,
        providerId: String? = null,
        agent: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
    ): Result<Unit> = repo.sendMessageAsync(sessionId, text, modelId, providerId, agent, imageBase64, imageMimeType)
}
