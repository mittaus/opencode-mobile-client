package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(
    val id: String,
    val title: String = "",
    @SerialName("projectID") val projectId: String = "",
    val directory: String = "",
    @SerialName("model") val modelId: String = "",
    @SerialName("provider") val providerId: String = "",
    @SerialName("time") val time: TimeDto = TimeDto(),
    @SerialName("share") val shareUrl: String? = null,
)

@Serializable
data class TimeDto(
    val created: Long = 0L,
    val updated: Long = 0L,
)
