package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeEvents(): Flow<ServerEvent>
    suspend fun getSessions(project: String? = null): Result<List<Session>>
    suspend fun createSession(projectId: String, title: String? = null, directory: String? = null): Result<Session>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun abortSession(sessionId: String): Result<Unit>
    suspend fun getTodo(sessionId: String): Result<List<TodoItem>>
    suspend fun getDiff(sessionId: String, messageId: String? = null): Result<List<FileDiff>>
    suspend fun revertMessage(sessionId: String, messageId: String): Result<Unit>
    suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean = false,
    ): Result<Unit>
}
