package com.opencode.shared.domain.model

data class FileDiff(
    val path: String,
    val status: FileStatus,
    val additions: Int,
    val deletions: Int,
    val hunks: List<DiffHunk>,
    val staged: Boolean = false,
)

enum class FileStatus { MODIFIED, ADDED, DELETED, RENAMED }

data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>,
)

data class DiffLine(
    val type: DiffLineType,
    val oldNumber: Int?,
    val newNumber: Int?,
    val content: String,
)

enum class DiffLineType { CONTEXT, ADDITION, DELETION }
