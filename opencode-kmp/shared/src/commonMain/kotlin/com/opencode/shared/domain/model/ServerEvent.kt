package com.opencode.shared.domain.model

sealed class ServerEvent {
    data class Connected(val version: String) : ServerEvent()
    data class MessageUpdated(val sessionId: String, val message: Message) : ServerEvent()
    data class SessionUpdated(val session: Session) : ServerEvent()
    data class PermissionRequested(val request: PermissionRequest) : ServerEvent()
    data class TodoUpdated(val sessionId: String, val todos: List<TodoItem>) : ServerEvent()
    data object SessionAborted : ServerEvent()
    data class SessionBusy(val sessionId: String) : ServerEvent()
    data class SessionIdle(val sessionId: String) : ServerEvent()
    /** Incremental text delta for a streaming assistant message */
    data class MessagePartDelta(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val field: String,
        val delta: String,
    ) : ServerEvent()
    data class Error(val message: String) : ServerEvent()
    data class Unknown(val type: String, val raw: String) : ServerEvent()
}
