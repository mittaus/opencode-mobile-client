package com.opencode.android.di

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.opencode.android.ui.screens.chat.ChatViewModel
import com.opencode.android.ui.screens.connect.ConnectViewModel
import com.opencode.android.ui.screens.files.FilesViewModel
import com.opencode.android.ui.screens.models.ModelsViewModel
import com.opencode.android.ui.screens.projects.ProjectsViewModel
import com.opencode.android.ui.screens.sessions.SessionsViewModel
import com.opencode.android.ui.screens.stats.StatsViewModel
import com.opencode.shared.data.local.AndroidPreferencesStorage
import com.opencode.shared.data.local.PreferencesStorage
import com.opencode.shared.di.domainModule
import com.opencode.shared.di.storageModule
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.dataStore by preferencesDataStore(name = "opencode_prefs")

val androidModule = module {
    // DataStore
    single { get<Context>().dataStore }
    single { AndroidPreferencesStorage(get()) } bind PreferencesStorage::class

    // ViewModels
    viewModel { ConnectViewModel(get<Application>(), get()) }
    viewModelOf(::StatsViewModel)
    viewModel { ProjectsViewModel(get(), get(), get()) }  // GetAvailableProjectsUseCase, RegisterProjectUseCase, OpenCodeApi

    // ViewModels with parameters
    viewModel { (projectPath: String, projectId: String) -> SessionsViewModel(projectPath, projectId, get(), get()) }
    viewModel { (sessionId: String) -> ChatViewModel(
        sessionId = sessionId,
        getMessages = get(), sendMessageAsync = get(), executeCommand = get(),
        abortSession = get(), revertMessage = get(), respondPermission = get(),
        observeEvents = get(), getSessions = get(), prefs = get(), statsRepo = get(),
    )}
    viewModelOf(::FilesViewModel)
    viewModel { (sessionId: String, currentModelId: String) -> ModelsViewModel(sessionId, currentModelId, get()) }
}

val allModules = listOf(androidModule, domainModule, storageModule)
