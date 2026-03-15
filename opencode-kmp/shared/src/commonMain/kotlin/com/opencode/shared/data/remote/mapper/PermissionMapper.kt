package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.PermissionRequestDto
import com.opencode.shared.domain.model.PermissionRequest

fun PermissionRequestDto.toDomain(): PermissionRequest {
    // New format (permission.asked): uses `permission` + `patterns`
    // Legacy format (permission.requested): uses `toolName` + `filePath`/`command`
    val effectiveTool = permission.ifBlank { toolName }
    val effectivePatterns = patterns.ifEmpty { listOfNotNull(filePath, command) }
    return PermissionRequest(
        id = id,
        sessionId = sessionId,
        toolName = effectiveTool,
        description = description.ifBlank { effectiveTool },
        patterns = effectivePatterns,
        command = command,
        filePath = effectivePatterns.firstOrNull() ?: filePath,
    )
}
