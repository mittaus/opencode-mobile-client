package com.opencode.shared.domain.repository

/**
 * Represents a project entry returned by the gateway's /projects endpoint.
 * @param path      Absolute path to the project directory (display-friendly).
 * @param worktree  Canonical worktree path sent as X-Project-Path header to the gateway.
 * @param port      Port the per-project OpenCode process listens on.
 * @param isRunning Whether the per-project OpenCode process is currently running.
 * @param status    Raw status string from the gateway: "running" | "stopped".
 */
data class AvailableProject(
    val path: String,
    val worktree: String = path,  // defaults to path for backward compat
    val projectId: String = "global",
    val port: Int = 0,
    val isRunning: Boolean = false,
    val status: String = "stopped",
    /** true = has its own projectID from GET /project; false = extracted from a global session's directory. */
    val isRegistered: Boolean = true,
) {
    /** Display name derived from the last path segment. */
    val name: String get() = worktree.ifBlank { path }.trimEnd('/', '\\')
        .substringAfterLast('/').substringAfterLast('\\')
        .ifBlank { worktree.ifBlank { path } }
}
