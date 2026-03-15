package com.opencode.shared.data.remote.api

import com.opencode.shared.data.remote.dto.*
import com.opencode.shared.domain.model.ServerConnection
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class OpenCodeApi(private val connection: ServerConnection) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Active project worktree path sent as X-Project-Path header to the gateway.
    // null means use the gateway's default (bootstrap project — backward compat).
    @Volatile
    var activeProjectPath: String? = null

    private fun buildClient(timeoutMs: Long? = 30_000L) = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.HEADERS }
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = if (timeoutMs == null) Long.MAX_VALUE else timeoutMs
        }
        expectSuccess = true
        defaultRequest {
            url(connection.baseUrl)
            contentType(ContentType.Application.Json)
            if (connection.apiKey.isNotBlank()) {
                header("X-API-Key", connection.apiKey)
            }
            // Route request to the correct per-project OpenCode instance.
            activeProjectPath?.takeIf { it.isNotBlank() }?.let {
                header("X-Project-Path", it)
            }
        }
    }

    private var client = buildClient(timeoutMs = 30_000L)
    private var sseClient = buildClient(timeoutMs = null)  // sin timeout — stream perpetuo

    /**
     * Updates the active project path and rebuilds the HTTP clients so subsequent requests
     * include the correct X-Project-Path header.
     * Call this when the user selects a different project in the Projects screen.
     */
    fun updateProjectPath(worktree: String?) {
        activeProjectPath = worktree
        client = buildClient(timeoutMs = 30_000L)
        sseClient = buildClient(timeoutMs = null)
        println("[OC-API] activeProjectPath updated: $worktree")
    }

    // ── Health ──
    /** Gateway's own health check — always works if the gateway is reachable. */
    suspend fun gatewayHealth(): String = client.get("/health").bodyAsText()

    suspend fun health(): HealthDto = client.get("/global/health").body()

    // ── Available Projects (gateway-managed list) ──
    /** Calls the gateway's /projects endpoint — returns all known projects with their status. */
    suspend fun getAvailableProjects(): List<AvailableProjectDto> = try {
        val resp = client.get("/projects")
        val raw = resp.bodyAsText()
        println("[OC-API] getAvailableProjects raw=$raw")
        json.decodeFromString(raw)
    } catch (e: Exception) {
        println("[OC-API] getAvailableProjects ✗ ${e::class.simpleName}: ${e.message}")
        emptyList()
    }

    // ── Projects (OpenCode CLI) ──
    suspend fun getProjects(): List<ProjectDto> {
        val resp = client.get("/project")
        val raw = resp.bodyAsText()
        println("[OC-API] getProjects raw=${raw.take(300)}")
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            println("[OC-API] getProjects ✗ ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
    suspend fun getCurrentProject(): ProjectDto = client.get("/project/current").body()
    suspend fun getVcs(): VcsInfoDto = client.get("/vcs").body()
    suspend fun getVcsDiff(): List<FileDiffDto> = client.get("/vcs/diff").body()

    suspend fun stageFiles(paths: List<String>) {
        client.post("/vcs/stage") {
            setBody(buildJsonObject { putJsonArray("paths") { paths.forEach { add(it) } } })
        }.bodyAsText()
    }

    suspend fun discardChanges(path: String, status: String) {
        client.post("/vcs/discard") {
            setBody(buildJsonObject { put("path", path); put("status", status) })
        }.bodyAsText()
    }

    suspend fun unstageFiles(paths: List<String>) {
        client.post("/vcs/unstage") {
            setBody(buildJsonObject { putJsonArray("paths") { paths.forEach { add(it) } } })
        }.bodyAsText()
    }

    suspend fun commitChanges(message: String) {
        client.post("/vcs/commit") {
            setBody(buildJsonObject { put("message", message) })
        }.bodyAsText()
    }

    // ── Providers ──
    suspend fun getProviders(): ProviderListDto {
        val resp = client.get("/provider")
        val raw = resp.bodyAsText()
        println("[OC-API] getProviders raw=${raw.take(500)}")
        // Log glm and minimax model entries for debugging
        val glmIdx = raw.indexOf("glm")
        if (glmIdx >= 0) println("[OC-API] getProviders glm context=${raw.substring(maxOf(0, glmIdx-50), minOf(raw.length, glmIdx+200))}")
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            println("[OC-API] getProviders ✗ ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    // ── Sessions ──
    suspend fun getSessions(project: String? = null): List<SessionDto> =
        client.get("/session") {
            if (project != null) parameter("project", project)
        }.body()

    suspend fun createSession(projectId: String, title: String? = null, directory: String? = null): SessionDto =
        client.post("/session") {
            if (directory != null) parameter("directory", directory)
            setBody(buildJsonObject {
                put("projectID", projectId)
                if (title != null) put("title", title)
                if (directory != null) put("directory", directory)
            })
        }.body()

    suspend fun deleteSession(id: String): Boolean =
        client.delete("/session/$id").body()

    suspend fun abortSession(id: String): Boolean =
        client.post("/session/$id/abort").body()

    suspend fun getTodo(id: String): List<TodoItemDto> =
        client.get("/session/$id/todo").body()

    suspend fun getDiff(id: String, messageId: String? = null): List<FileDiffDto> =
        client.get("/session/$id/diff") {
            if (messageId != null) parameter("messageID", messageId)
        }.body()

    suspend fun revertMessage(sessionId: String, messageId: String): Boolean =
        client.post("/session/$sessionId/revert") {
            setBody(mapOf("messageID" to messageId))
        }.body()

    suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        dto: PermissionResponseDto,
    ): Boolean {
        val httpResponse = client.post("/session/$sessionId/permissions/$permissionId") {
            setBody(dto)
        }
        println("[OC-API] respondToPermission status=${httpResponse.status.value} session=$sessionId permId=$permissionId")
        if (!httpResponse.status.isSuccess()) {
            val body = runCatching { httpResponse.bodyAsText() }.getOrElse { "" }
            println("[OC-API] respondToPermission ERROR body=$body")
        }
        return httpResponse.body()
    }

    // ── Messages ──
    suspend fun getMessages(sessionId: String): List<MessageListItemDto> =
        client.get("/session/$sessionId/message").body()

    suspend fun sendMessage(
        sessionId: String,
        text: String,
        modelId: String? = null,
        providerId: String? = null,
        agent: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
    ): MessageListItemDto {
        println("[OC-API] sendMessage → session=$sessionId model=$modelId provider=$providerId agent=$agent text=\"${text.take(80)}\"")
        return try {
            val result: MessageListItemDto = client.post("/session/$sessionId/message") {
                setBody(buildJsonObject {
                    putJsonArray("parts") { 
                        val finalMsg = if (imageBase64 != null && imageMimeType != null) {
                            "![image](data:$imageMimeType;base64,$imageBase64)\n$text"
                        } else {
                            text
                        }
                        if (finalMsg.isNotBlank()) {
                            addJsonObject { put("type", "text"); put("text", finalMsg) }
                        }
                    }
                    if (modelId != null) {
                        put("model", buildJsonObject {
                            put("modelID", modelId)
                            if (providerId != null) put("providerID", providerId)
                        })
                    }
                    if (agent != null) put("agent", agent)
                })
            }.body()
            println("[OC-API] sendMessage ✓ → msgId=${result.info.id} role=${result.info.role} sessionID=${result.info.sessionId}")
            result
        } catch (e: Exception) {
            println("[OC-API] sendMessage ✗ → ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    suspend fun sendMessageAsync(
        sessionId: String,
        text: String,
        modelId: String? = null,
        providerId: String? = null,
        agent: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
    ) {
        val body = buildJsonObject {
            putJsonArray("parts") { 
                        val finalMsg = if (imageBase64 != null && imageMimeType != null) {
                            "![image](data:$imageMimeType;base64,$imageBase64)\n$text"
                        } else {
                            text
                        }
                        if (finalMsg.isNotBlank()) {
                            addJsonObject { put("type", "text"); put("text", finalMsg) }
                        }
                    }
            if (modelId != null) {
                put("model", buildJsonObject {
                    put("modelID", modelId)
                    if (providerId != null) put("providerID", providerId)
                })
            }
            if (agent != null) put("agent", agent)
        }
        println("[OC-API] sendMessageAsync body=$body")
        client.post("/session/$sessionId/prompt_async") {
            setBody(body)
        }.bodyAsText()
    }

    suspend fun executeCommand(
        sessionId: String,
        command: String,
        arguments: String = "",
        agent: String? = null,
    ): MessageListItemDto =
        client.post("/session/$sessionId/command") {
            setBody(buildJsonObject {
                put("command", command)
                put("arguments", arguments)
            })
        }.body()

    // ── Register project ──
    suspend fun registerProject(directory: String): ProjectDto {
        val resp: RegisterProjectResponseDto = client.post("/project/register") {
            setBody(buildJsonObject { put("directory", directory) })
        }.body()
        return resp.project
    }

    // ── Process (gateway-specific) ──
    suspend fun switchProject(directory: String) {
        client.post("/process/switch") {
            parameter("directory", directory)
        }.bodyAsText()
    }

    /** Starts opencode via the gateway. Returns true if started, false if already running (409). */
    suspend fun startProcess(): Boolean = try {
        client.post("/process/start").bodyAsText()
        true
    } catch (e: Exception) {
        // 409 Conflict = already running, that's fine
        false
    }

    // ── SSE Event stream ──
    fun observeEvents(): Flow<String> = flow {
        println("[OC-SSE] connecting to /event …")
        sseClient.prepareGet("/event").execute { response ->
            println("[OC-SSE] connected, status=${response.status}")
            val channel: ByteReadChannel = response.bodyAsChannel()
            var eventCount = 0
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    eventCount++
                    println("[OC-SSE] event #$eventCount raw=${data.take(300)}")
                    emit(data)
                }
            }
            println("[OC-SSE] channel closed after $eventCount events")
        }
    }

    fun close() {
        client.close()
        sseClient.close()
    }
}
