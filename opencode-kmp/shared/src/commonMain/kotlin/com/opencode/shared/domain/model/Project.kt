package com.opencode.shared.domain.model

data class Project(
    val id: String,
    val path: String,
    val name: String,
    val gitBranch: String? = null,
    val gitRemote: String? = null,
    val language: String? = null,
)
