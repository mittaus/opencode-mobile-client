package com.opencode.shared.domain.usecase.message

import com.opencode.shared.domain.model.Message
import com.opencode.shared.domain.repository.MessageRepository

class ExecuteCommandUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(
        sessionId: String,
        command: String,
        arguments: String = "",
        agent: String? = null,
    ): Result<Message> = repo.executeCommand(sessionId, command, arguments, agent)
}
