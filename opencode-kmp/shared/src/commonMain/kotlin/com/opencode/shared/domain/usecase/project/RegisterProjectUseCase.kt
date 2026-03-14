package com.opencode.shared.domain.usecase.project

import com.opencode.shared.domain.repository.ProjectRepository

class RegisterProjectUseCase(private val repo: ProjectRepository) {
    /** Creates a new session in the given directory (projectID="global"), then the project list is refreshed by the caller. */
    suspend operator fun invoke(directory: String): Result<Unit> = repo.createSessionForDirectory(directory)
}
