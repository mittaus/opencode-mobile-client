package com.opencode.shared.domain.model

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val toolName: String,
    val description: String,
    val command: String?,
    val filePath: String?,
)

enum class PermissionResponse { ALLOW, DENY, ALLOW_ALWAYS }
