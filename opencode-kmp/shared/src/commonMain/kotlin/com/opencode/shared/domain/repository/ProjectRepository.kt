package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.FileDiff
import com.opencode.shared.domain.model.Project

interface ProjectRepository {
    /** Returns the list of all known projects from the gateway (/projects endpoint). */
    suspend fun getAvailableProjects(): Result<List<AvailableProject>>

    suspend fun getProjects(): Result<List<Project>>
    suspend fun getCurrentProject(): Result<Project>
    suspend fun registerProject(directory: String): Result<Unit>
    suspend fun createSessionForDirectory(directory: String): Result<Unit>
    suspend fun switchProject(directory: String): Result<Unit>
    suspend fun getVcsDiff(): Result<List<FileDiff>>
    suspend fun stageFiles(paths: List<String>): Result<Unit>
    suspend fun unstageFiles(paths: List<String>): Result<Unit>
    suspend fun discardChanges(path: String, status: String): Result<Unit>
    suspend fun commitChanges(message: String): Result<Unit>
}
