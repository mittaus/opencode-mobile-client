package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.model.Session
import com.opencode.shared.domain.repository.SessionRepository

class CreateSessionUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(projectId: String, title: String? = null, directory: String? = null): Result<Session> =
        repo.createSession(projectId, title, directory)
}
