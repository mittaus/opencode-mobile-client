package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.repository.SessionRepository

class RevertMessageUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(sessionId: String, messageId: String): Result<Unit> =
        repo.revertMessage(sessionId, messageId)
}
