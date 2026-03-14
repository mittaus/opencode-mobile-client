package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileDiffDto(
    val path: String,
    val status: String = "modified",    // modified | added | deleted | renamed
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String = "",             // raw unified diff patch
    val staged: Boolean = false,
)
