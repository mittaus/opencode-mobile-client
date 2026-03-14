package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.AvailableProject
import com.opencode.shared.domain.repository.ProjectRepository

class GetAvailableProjectsUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(): Result<List<AvailableProject>> = repo.getAvailableProjects()
}
