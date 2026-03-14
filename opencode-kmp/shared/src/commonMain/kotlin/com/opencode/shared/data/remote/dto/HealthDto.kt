package com.opencode.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val healthy: Boolean = false,
    val version: String = "",
)
