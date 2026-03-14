package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the gateway's /projects endpoint.
 * Shape: { "worktree": "...", "port": 4096, "status": "running" | "stopped" }
 */
@Serializable
data class AvailableProjectDto(
    val worktree: String = "",
    val port: Int = 0,
    val status: String = "stopped",
    // Legacy field — kept for backward compat if old gateway returns "path"
    @SerialName("path") val legacyPath: String? = null,
) {
    /** Canonical project path: prefer worktree, fall back to legacyPath. */
    val resolvedPath: String get() = worktree.ifBlank { legacyPath.orEmpty() }
}
