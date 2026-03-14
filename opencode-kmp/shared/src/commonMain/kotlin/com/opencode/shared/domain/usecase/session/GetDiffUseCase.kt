package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.repository.SessionRepository

class GetDiffUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(sessionId: String, messageId: String? = null): Result<List<FileDiff>> =
        repo.getDiff(sessionId, messageId)
}
