package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.ProjectRepository

class DiscardChangesUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(path: String, status: String): Result<Unit> =
        repo.discardChanges(path, status)
}
