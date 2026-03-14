package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.PermissionRequestDto
import com.opencode.shared.domain.model.PermissionRequest

fun PermissionRequestDto.toDomain() = PermissionRequest(
    id = id,
    sessionId = sessionId,
    toolName = toolName,
    description = description,
    command = command,
    filePath = filePath,
)
