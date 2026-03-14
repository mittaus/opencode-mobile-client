package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.FileDiffDto
import com.opencode.shared.domain.model.*

fun FileDiffDto.toDomain(): FileDiff = FileDiff(
    path = path,
    status = when (status) {
        "added" -> FileStatus.ADDED
        "deleted" -> FileStatus.DELETED
        "renamed" -> FileStatus.RENAMED
        else -> FileStatus.MODIFIED
    },
    additions = additions,
    deletions = deletions,
    hunks = parsePatch(patch),
    staged = staged,
)

private fun parsePatch(patch: String): List<DiffHunk> {
    if (patch.isBlank()) return emptyList()
    val hunks = mutableListOf<DiffHunk>()
    var currentHeader = ""
    val currentLines = mutableListOf<DiffLine>()
    var oldN = 0; var newN = 0

    for (raw in patch.lines()) {
        when {
            raw.startsWith("@@") -> {
                if (currentHeader.isNotBlank()) {
                    hunks.add(DiffHunk(currentHeader, currentLines.toList()))
                    currentLines.clear()
                }
                currentHeader = raw
                val match = Regex("""@@ -(\d+).*\+(\d+)""").find(raw)
                oldN = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                newN = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            }
            raw.startsWith("+") && !raw.startsWith("+++") -> {
                currentLines.add(DiffLine(DiffLineType.ADDITION, null, newN++, raw))
            }
            raw.startsWith("-") && !raw.startsWith("---") -> {
                currentLines.add(DiffLine(DiffLineType.DELETION, oldN++, null, raw))
            }
            else -> {
                if (currentHeader.isNotBlank())
                    currentLines.add(DiffLine(DiffLineType.CONTEXT, oldN++, newN++, raw))
            }
        }
    }
    if (currentHeader.isNotBlank()) hunks.add(DiffHunk(currentHeader, currentLines.toList()))
    return hunks
}
