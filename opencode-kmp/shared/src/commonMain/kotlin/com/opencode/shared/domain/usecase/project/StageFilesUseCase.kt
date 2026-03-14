package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.ProjectRepository

class StageFilesUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(paths: List<String>): Result<Unit> = repo.stageFiles(paths)
}
