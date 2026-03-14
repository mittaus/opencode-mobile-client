package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.ProjectDto
import com.opencode.shared.domain.model.Project

fun ProjectDto.toDomain(): Project = Project(
    id = id,
    path = path,
    name = path.split("/", "\\").filter { it.isNotBlank() }.lastOrNull() ?: path,
    gitBranch = null,
    gitRemote = null,
)
