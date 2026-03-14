package com.opencode.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TodoItemDto(
    val id: String,
    val content: String = "",
    val status: String = "pending",     // pending | in_progress | completed
    val priority: String = "medium",    // low | medium | high
)
