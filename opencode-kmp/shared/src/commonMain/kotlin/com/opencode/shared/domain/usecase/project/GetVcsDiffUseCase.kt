package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.repository.ProjectRepository

class GetVcsDiffUseCase(private val repo: ProjectRepository) {
    suspend operator fun invoke(): Result<List<FileDiff>> = repo.getVcsDiff()
}
