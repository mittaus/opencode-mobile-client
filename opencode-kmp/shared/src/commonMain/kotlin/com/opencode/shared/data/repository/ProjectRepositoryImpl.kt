package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.remote.mapper.toDomain
import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.model.Project
import com.opencode.shared.domain.repository.AvailableProject
import com.opencode.shared.domain.repository.ProjectRepository

class ProjectRepositoryImpl(private val api: OpenCodeApi) : ProjectRepository {

    override suspend fun getAvailableProjects(): Result<List<AvailableProject>> = runCatching {
        // 1. Real projects: GET /project, filter out "global"
        val registeredProjects: List<AvailableProject> = try {
            api.getProjects()
                .filter { it.id != "global" }
                .map { dto ->
                    AvailableProject(
                        path = dto.path,
                        worktree = dto.path,
                        projectId = dto.id,
                        isRegistered = true,
                    )
                }
        } catch (e: Exception) {
            println("[ProjectRepo] getProjects ✗ ${e.message}")
            emptyList()
        }

        // Collect registered worktrees for deduplication
        val registeredWorktrees = registeredProjects.map { it.worktree }.toSet()

        // 2. Session directories: GET /session where projectID == "global"
        val sessionProjects: List<AvailableProject> = try {
            api.getSessions()
                .filter { it.projectId == "global" && it.directory.isNotBlank() }
                .map { it.directory }
                .distinct()
                .filter { dir -> dir !in registeredWorktrees }
                .map { dir ->
                    AvailableProject(
                        path = dir,
                        worktree = dir,
                        isRegistered = false,
                    )
                }
        } catch (e: Exception) {
            println("[ProjectRepo] getSessions ✗ ${e.message}")
            emptyList()
        }

        registeredProjects + sessionProjects
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
