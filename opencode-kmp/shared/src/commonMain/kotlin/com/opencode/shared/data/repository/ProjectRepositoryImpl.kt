package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.remote.mapper.toDomain
import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.model.Project
import com.opencode.shared.domain.repository.AvailableProject
import com.opencode.shared.domain.repository.ProjectRepository

class ProjectRepositoryImpl(private val api: OpenCodeApi) : ProjectRepository {

    override suspend fun getAvailableProjects(): Result<List<AvailableProject>> = runCatching {
        // 1. Real projects: GET /project, deduplicate by worktree path.
        // OpenCode can return the same directory twice: once with a human-readable id
        // (registered manually) and once with a git-hash id (auto-detected). Keep the
        // human-readable one by sorting so non-hex ids come first, then distinctBy worktree.
        val registeredProjects: List<AvailableProject> = try {
            val hexPattern = Regex("^[0-9a-f]{40}$")
            api.getProjects()
                .filter { it.path.isNotBlank() }
                .sortedBy { if (hexPattern.matches(it.id)) 1 else 0 }  // prefer named ids
                .map { dto ->
                    AvailableProject(
                        path = dto.path.trimEnd('/'),
                        worktree = dto.path.trimEnd('/'),
                        projectId = dto.id,
                        isRegistered = dto.id != "global",
                    )
                }
                .distinctBy { it.worktree }
        } catch (e: Exception) {
            println("[ProjectRepo] getProjects ✗ ${e.message}")
            emptyList()
        }

        registeredProjects
    }

    override suspend fun getProjects(): Result<List<Project>> = runCatching {
        api.getProjects().map { it.toDomain() }
    }

    override suspend fun getCurrentProject(): Result<Project> = runCatching {
        api.getCurrentProject().toDomain()
    }

    override suspend fun registerProject(directory: String): Result<Unit> = runCatching {
        api.registerProject(directory)
        Unit
    }

    override suspend fun createSessionForDirectory(directory: String): Result<Unit> = runCatching {
        api.createSession(projectId = "global", directory = directory)
        Unit
    }

    override suspend fun switchProject(directory: String): Result<Unit> = runCatching {
        api.switchProject(directory)
    }

    override suspend fun getVcsDiff(): Result<List<FileDiff>> = runCatching {
        api.getVcsDiff().map { it.toDomain() }
    }

    override suspend fun stageFiles(paths: List<String>): Result<Unit> = runCatching {
        api.stageFiles(paths)
    }

    override suspend fun unstageFiles(paths: List<String>): Result<Unit> = runCatching {
        api.unstageFiles(paths)
    }

    override suspend fun discardChanges(path: String, status: String): Result<Unit> = runCatching {
        api.discardChanges(path, status)
    }

    override suspend fun commitChanges(message: String): Result<Unit> = runCatching {
        api.commitChanges(message)
    }
}
