package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.model.Project
import com.opencode.shared.domain.repository.ProjectRepository

class GetCurrentProjectUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(): Result<Project> = repo.getCurrentProject()
}
