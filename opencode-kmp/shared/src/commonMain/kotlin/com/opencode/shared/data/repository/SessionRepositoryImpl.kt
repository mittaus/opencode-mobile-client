package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.SseEventParser
import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.remote.dto.PermissionResponseDto
import com.opencode.shared.data.remote.mapper.*
import com.opencode.shared.domain.model.*
import com.opencode.shared.domain.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class SessionRepositoryImpl(
    private val api: OpenCodeApi,
    private val parser: SseEventParser,
) : SessionRepository {

    override fun observeEvents(): Flow<ServerEvent> =
        api.observeEvents()
            .map { parser.parse(it) }
            .retryWhen { _, attempt ->
                delay(minOf(2_000L * (attempt + 1), 15_000L))
                true  // retry indefinitely
            }

    override suspend fun getSessions(project: String?): Result<List<Session>> = runCatching {
        val sessions = api.getSessions(project).map { it.toDomain() }
        if (project != null) {
            val filtered = sessions.filter { it.directory == project }
            println("[SessionRepo] getSessions filter project=$project total=${sessions.size} matched=${filtered.size}")
            sessions.forEach { println("[SessionRepo]   session id=${it.id} directory='${it.directory}' match=${it.directory == project}") }
            filtered
        } else sessions
    }

    override suspend fun createSession(projectId: String, title: String?, directory: String?): Result<Session> = runCatching {
        api.createSession(projectId, title, directory).toDomain()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        api.deleteSession(sessionId)
        Unit
    }

    override suspend fun abortSession(sessionId: String): Result<Unit> = runCatching {
        api.abortSession(sessionId)
        Unit
    }

    override suspend fun getTodo(sessionId: String): Result<List<TodoItem>> = runCatching {
        api.getTodo(sessionId).map { it.toDomain() }
    }

    override suspend fun getDiff(
        sessionId: String,
        messageId: String?,
    ): Result<List<FileDiff>> = runCatching {
        api.getDiff(sessionId, messageId).map { it.toDomain() }
    }

    override suspend fun revertMessage(
        sessionId: String,
        messageId: String,
    ): Result<Unit> = runCatching {
        api.revertMessage(sessionId, messageId)
        Unit
    }

    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean,
    ): Result<Unit> = runCatching {
        api.respondToPermission(
            sessionId, permissionId,
            PermissionResponseDto(
                response = if (response == PermissionResponse.DENY) "deny" else "allow",
                remember = remember || response == PermissionResponse.ALLOW_ALWAYS,
            )
        )
        Unit
    }
}
