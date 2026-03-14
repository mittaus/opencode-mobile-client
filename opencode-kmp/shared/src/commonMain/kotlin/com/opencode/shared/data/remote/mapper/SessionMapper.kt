package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.SessionDto
import com.opencode.shared.domain.model.Session

fun SessionDto.toDomain(): Session = Session(
    id = id,
    title = title.ifBlank { "Sin título" },
    projectId = projectId,
    directory = directory,
    modelId = modelId,
    providerId = providerId,
    createdAt = time.created,
    updatedAt = time.updated,
    shareUrl = shareUrl,
)
