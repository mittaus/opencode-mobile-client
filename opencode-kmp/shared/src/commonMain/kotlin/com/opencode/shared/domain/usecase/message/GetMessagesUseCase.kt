package com.opencode.shared.domain.usecase.message

import com.opencode.shared.domain.model.Message
import com.opencode.shared.domain.repository.MessageRepository

class GetMessagesUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(sessionId: String): Result<List<Message>> =
        repo.getMessages(sessionId)
}
