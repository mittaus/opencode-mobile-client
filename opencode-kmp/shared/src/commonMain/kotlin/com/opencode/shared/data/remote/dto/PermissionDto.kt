package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequestDto(
    val id: String,
    @SerialName("sessionID") val sessionId: String = "",
    @SerialName("toolName") val toolName: String = "",
    val description: String = "",
    val command: String? = null,
    @SerialName("filePath") val filePath: String? = null,
)

@Serializable
data class PermissionResponseDto(
    val response: String,   // "allow" | "deny"
    val remember: Boolean = false,
)
