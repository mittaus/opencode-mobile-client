package com.opencode.shared.domain.model

data class TodoItem(
    val id: String,
    val content: String,
    val status: TodoStatus,
    val priority: TodoPriority = TodoPriority.MEDIUM,
)

enum class TodoStatus { PENDING, IN_PROGRESS, COMPLETED }
enum class TodoPriority { LOW, MEDIUM, HIGH }
