package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.ProjectRepository

class SwitchProjectUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(directory: String): Result<Unit> = repo.switchProject(directory)
}
