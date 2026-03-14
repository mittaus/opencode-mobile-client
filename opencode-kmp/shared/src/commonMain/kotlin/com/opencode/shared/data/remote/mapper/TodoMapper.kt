package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.TodoItemDto
import com.opencode.shared.domain.model.*

fun TodoItemDto.toDomain() = TodoItem(
    id = id,
    content = content,
    status = when (status) {
        "in_progress" -> TodoStatus.IN_PROGRESS
        "completed" -> TodoStatus.COMPLETED
        else -> TodoStatus.PENDING
    },
    priority = when (priority) {
        "high" -> TodoPriority.HIGH
        "low" -> TodoPriority.LOW
        else -> TodoPriority.MEDIUM
    },
)
