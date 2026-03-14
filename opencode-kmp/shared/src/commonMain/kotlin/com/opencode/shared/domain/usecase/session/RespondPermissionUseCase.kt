package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.model.PermissionResponse
import com.opencode.shared.domain.repository.SessionRepository

class RespondPermissionUseCase(private val repo: SessionRepository) {
    suspend operator fun invoke(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean = false,
    ): Result<Unit> = repo.respondToPermission(sessionId, permissionId, response, remember)
}
