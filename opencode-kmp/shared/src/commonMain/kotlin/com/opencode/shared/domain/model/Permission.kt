package com.opencode.shared.domain.model

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val toolName: String,       // permission type: "read", "write", "bash", etc.
    val description: String,
    val patterns: List<String>, // affected file paths or patterns
    val command: String?,
    val filePath: String?,      // first pattern (convenience)
)

enum class PermissionResponse { ALLOW, DENY, ALLOW_ALWAYS }
