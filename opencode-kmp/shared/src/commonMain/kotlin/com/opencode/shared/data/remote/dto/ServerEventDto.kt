package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Raw SSE event parsed from the /event stream
@Serializable
data class ServerEventDto(
    val type: String,
    val properties: JsonElement? = null,
)

@Serializable
data class ServerConnectedDto(
    val version: String = "",
)
