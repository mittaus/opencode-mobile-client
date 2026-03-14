package com.opencode.shared.domain.model

data class Session(
    val id: String,
    val title: String,
    val projectId: String,
    val directory: String = "",
    val modelId: String,
    val providerId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = false,
    val shareUrl: String? = null,
)
