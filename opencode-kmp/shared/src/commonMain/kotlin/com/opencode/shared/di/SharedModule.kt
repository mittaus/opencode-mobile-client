package com.opencode.shared.di

import com.opencode.shared.data.remote.SseEventParser
import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.repository.*
import com.opencode.shared.domain.repository.*
import com.opencode.shared.domain.usecase.message.*
import com.opencode.shared.domain.usecase.project.*
import com.opencode.shared.domain.usecase.session.*
import com.opencode.shared.domain.usecase.stats.*
import com.opencode.shared.domain.model.ServerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

// ── Active project state ───────────────────────────────────────────────────────
// Holds the worktree path of the currently selected project.
// null = use the gateway's default (bootstrap project).
private val _activeProjectPath = MutableStateFlow<String?>(null)
val activeProjectPath: StateFlow<String?> = _activeProjectPath.asStateFlow()

/**
 * Call this from the Projects screen when the user selects a project.
 * Updates the StateFlow and reconfigures OpenCodeApi to send X-Project-Path.
 */
fun setActiveProject(worktree: String?, api: OpenCodeApi) {
    _activeProjectPath.value = worktree
    api.updateProjectPath(worktree)
}

// Called once a valid connection is established
fun networkModule(connection: ServerConnection) = module {
    single { OpenCodeApi(connection) }
    single { SseEventParser() }
    single { SessionRepositoryImpl(get(), get()) } bind SessionRepository::class
    single { MessageRepositoryImpl(get()) } bind MessageRepository::class
    single { ProjectRepositoryImpl(get()) } bind ProjectRepository::class
    single { ProviderRepositoryImpl(get()) } bind ProviderRepository::class
}

val domainModule = module {
    // Session
    singleOf(::GetSessionsUseCase)
    singleOf(::CreateSessionUseCase)
    singleOf(::AbortSessionUseCase)
    singleOf(::GetDiffUseCase)
    singleOf(::RespondPermissionUseCase)
    singleOf(::RevertMessageUseCase)
    singleOf(::ObserveEventsUseCase)
    // Message
    singleOf(::SendMessageUseCase)
    singleOf(::SendMessageAsyncUseCase)
    singleOf(::GetMessagesUseCase)
    singleOf(::ExecuteCommandUseCase)
    // Project
    singleOf(::GetAvailableProjectsUseCase)
    singleOf(::RegisterProjectUseCase)
    singleOf(::GetProjectsUseCase)
    singleOf(::GetCurrentProjectUseCase)
    singleOf(::SwitchProjectUseCase)
    singleOf(::GetVcsDiffUseCase)
    singleOf(::StageFilesUseCase)
    singleOf(::UnstageFilesUseCase)
    singleOf(::DiscardChangesUseCase)
    singleOf(::CommitChangesUseCase)
    // Stats
    singleOf(::GetStatsUseCase)
}

val storageModule = module {
    single { ConnectionRepositoryImpl(get()) } bind ConnectionRepository::class
    single { StatsRepositoryImpl(get()) } bind StatsRepository::class
}
