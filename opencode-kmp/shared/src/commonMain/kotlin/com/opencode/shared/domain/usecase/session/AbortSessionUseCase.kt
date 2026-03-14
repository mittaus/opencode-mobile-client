package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.repository.SessionRepository

class AbortSessionUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(sessionId: String): Result<Unit> =
        repo.abortSession(sessionId)
}
