package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.ProjectRepository

class CommitChangesUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(message: String): Result<Unit> = repo.commitChanges(message)
}
