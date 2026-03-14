package com.opencode.shared.data.remote

import com.opencode.shared.data.remote.dto.*
import com.opencode.shared.data.remote.mapper.*
import com.opencode.shared.domain.model.ServerEvent
import kotlinx.serialization.json.*

class SseEventParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): ServerEvent {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: run {
                println("[OC-PARSER] no 'type' field in: ${raw.take(100)}")
                return ServerEvent.Unknown("", raw)
            }
            val props = obj["properties"]
            println("[OC-PARSER] type=$type props_keys=${props?.jsonObject?.keys}")

            when (type) {
                "server.connected" -> {
                    val v = props?.jsonObject?.get("version")?.jsonPrimitive?.content ?: ""
                    println("[OC-PARSER] ✓ Connected version=$v")
                    ServerEvent.Connected(v)
                }
                "session.updated", "session.created" -> {
                    // OpenCode wraps the session under properties.info
                    val sessionJson = props?.jsonObject?.get("info") ?: props
                    val dto = sessionJson?.let { runCatching { json.decodeFromJsonElement<SessionDto>(it) }.getOrNull() }
                        ?: return ServerEvent.Unknown(type, raw)
                    println("[OC-PARSER] ✓ SessionUpdated id=${dto.id}")
                    ServerEvent.SessionUpdated(dto.toDomain())
                }
                "message.updated", "message.created" -> {
                    // OpenCode sends the MessageListItemDto directly as properties (info + parts at top level)
                    // sessionID lives inside properties.info.sessionID
                    val msgDto = props?.let { runCatching { json.decodeFromJsonElement<MessageListItemDto>(it) }.getOrNull() }
                        ?: run {
                            println("[OC-PARSER] ✗ failed to decode MessageListItemDto, raw=${raw.take(200)}")
                            return ServerEvent.Unknown(type, raw)
                        }
                    val sessionId = msgDto.info.sessionId
                    println("[OC-PARSER] ✓ MessageUpdated session=$sessionId msgId=${msgDto.info.id} role=${msgDto.info.role} parts=${msgDto.parts.size}")
                    ServerEvent.MessageUpdated(sessionId, msgDto.toDomain())
                }
                "message.part.updated" -> {
                    // properties.part contains the full part — ignore, final content arrives via POST response
                    ServerEvent.Unknown(type, raw)
                }
                "message.part.delta" -> {
                    val p = props?.jsonObject ?: return ServerEvent.Unknown(type, raw)
                    val sessionId = p["sessionID"]?.jsonPrimitive?.content ?: ""
                    val messageId = p["messageID"]?.jsonPrimitive?.content ?: ""
                    val partId    = p["partID"]?.jsonPrimitive?.content ?: ""
                    val field     = p["field"]?.jsonPrimitive?.content ?: ""
                    val delta     = p["delta"]?.jsonPrimitive?.content ?: ""
                    ServerEvent.MessagePartDelta(sessionId, messageId, partId, field, delta)
                }
                "permission.requested" -> {
                    val dto = props?.let { json.decodeFromJsonElement<PermissionRequestDto>(it) }
                        ?: return ServerEvent.Unknown(type, raw)
                    println("[OC-PARSER] ✓ PermissionRequested id=${dto.id}")
                    ServerEvent.PermissionRequested(dto.toDomain())
                }
                "todo.updated" -> {
                    val sessionId = props?.jsonObject?.get("sessionID")
                        ?.jsonPrimitive?.content ?: ""
                    val items = props?.jsonObject?.get("todos")?.let {
                        json.decodeFromJsonElement<List<TodoItemDto>>(it)
                    } ?: emptyList()
                    println("[OC-PARSER] ✓ TodoUpdated session=$sessionId items=${items.size}")
                    ServerEvent.TodoUpdated(sessionId, items.map { it.toDomain() })
                }
                "session.aborted" -> {
                    println("[OC-PARSER] ✓ SessionAborted")
                    ServerEvent.SessionAborted
                }
                "session.status" -> {
                    val status = props?.jsonObject?.get("status")
                        ?.jsonObject?.get("type")?.jsonPrimitive?.content ?: ""
                    val sessionId = props?.jsonObject?.get("sessionID")
                        ?.jsonPrimitive?.content ?: ""
                    println("[OC-PARSER] ✓ SessionStatus session=$sessionId status=$status")
                    when (status) {
                        "busy" -> ServerEvent.SessionBusy(sessionId)
                        "idle" -> ServerEvent.SessionIdle(sessionId)
                        else -> ServerEvent.Unknown(type, raw)
                    }
                }
                "session.error" -> {
                    val p = props?.jsonObject ?: return ServerEvent.Unknown(type, raw)
                    val sessionId = p["sessionID"]?.jsonPrimitive?.content ?: ""
                    val errorObj  = p["error"]?.jsonObject
                    val errName   = errorObj?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                    val errData   = errorObj?.get("data")?.jsonObject
                    val errMsg    = errData?.get("message")?.jsonPrimitive?.content
                        ?: errorObj?.get("message")?.jsonPrimitive?.content
                        ?: raw
                    println("[OC-PARSER] ✓ SessionError session=$sessionId name=$errName msg=$errMsg")
                    ServerEvent.Error("[$errName] $errMsg")
                }
                "server.heartbeat", "session.idle", "session.diff" -> {
                    // informational — no action needed
                    ServerEvent.Unknown(type, raw)
                }
                else -> {
                    println("[OC-PARSER] ? Unknown type=$type")
                    ServerEvent.Unknown(type, raw)
                }
            }
        } catch (e: Exception) {
            println("[OC-PARSER] ✗ Exception ${e::class.simpleName}: ${e.message} | raw=${raw.take(200)}")
            ServerEvent.Error("Parse error: ${e.message}")
        }
    }
}
