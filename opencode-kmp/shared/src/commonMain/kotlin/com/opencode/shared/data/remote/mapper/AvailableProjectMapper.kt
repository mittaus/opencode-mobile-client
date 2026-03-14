package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.AvailableProjectDto
import com.opencode.shared.domain.repository.AvailableProject

fun AvailableProjectDto.toDomain() = AvailableProject(
    path = resolvedPath,
    worktree = worktree,
    port = port,
    isRunning = status == "running",
    status = status,
    isRegistered = true,
)
