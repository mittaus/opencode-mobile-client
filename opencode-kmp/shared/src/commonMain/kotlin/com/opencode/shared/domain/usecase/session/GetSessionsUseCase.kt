package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.model.Session
import com.opencode.shared.domain.repository.SessionRepository

class GetSessionsUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(project: String? = null): Result<List<Session>> =
        repo.getSessions(project)
}
