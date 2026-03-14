package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    @SerialName("worktree") val path: String,
    val vcs: String? = null,
)

/** Wrapper returned by the gateway's POST /project/register endpoint. */
@Serializable
data class RegisterProjectResponseDto(
    val project: ProjectDto,
)

@Serializable
data class VcsInfoDto(
    val branch: String? = null,
    val remote: String? = null,
)
