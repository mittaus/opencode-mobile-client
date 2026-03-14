package com.opencode.shared.domain.model

data class AttachmentData(
    val filename: String,
    val mimeType: String,
    val base64: String,
)
