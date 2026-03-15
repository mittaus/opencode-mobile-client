package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequestDto(
    val id: String,
    @SerialName("sessionID") val sessionId: String = "",
    // New format: permission.asked
    val permission: String = "",          // "read" | "write" | "bash" | "web" …
    val patterns: List<String> = emptyList(),
    // Legacy format: permission.requested (kept for backward compat)
    @SerialName("toolName") val toolName: String = "",
    val description: String = "",
    val command: String? = null,
    @SerialName("filePath") val filePath: String? = null,
)

@Serializable
data class PermissionResponseDto(
    val response: String,   // "once" | "always" | "reject"
)
