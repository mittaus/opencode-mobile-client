package com.opencode.android.ui.screens.connect

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.service.OpenCodeSseService
import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.di.networkModule
import com.opencode.shared.domain.model.ServerConnection
import com.opencode.shared.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules

data class ConnectUiState(
    val host: String = "192.168.1.100",
    val port: String = "8080",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ConnectViewModel(
    private val app: Application,
    private val connectionRepo: ConnectionRepository,
) : ViewModel() {

    companion object {
        private var loadedNetworkModule: org.koin.core.module.Module? = null
    }

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private val _navigateToProjects = MutableSharedFlow<Unit>()
    val navigateToProjects = _navigateToProjects.asSharedFlow()

    init {
        viewModelScope.launch {
            connectionRepo.getConnection().collect { saved ->
                if (saved != null) {
                    _state.update {
                        it.copy(
                            host = saved.host,
                            port = saved.port.toString(),
                            apiKey = saved.apiKey,
                        )
                    }
                }
            }
        }
    }

    private fun startOrRestartSseService() {
        val intent = Intent(app, OpenCodeSseService::class.java)
        // If already running, send RESTART so it picks up the new network module.
        // If not running, startForegroundService starts it fresh.
        if (isServiceRunning()) {
            // Already running — nothing to do, it keeps the process alive regardless
            android.util.Log.d("OC-Connect", "SSE service already running")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        android.util.Log.d("OC-Connect", "SSE service started")
    }

    private fun isServiceRunning(): Boolean {
        val manager = app.getSystemService(android.app.ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == OpenCodeSseService::class.java.name
        }
    }

    fun onHostChange(v: String) = _state.update { it.copy(host = v, error = null) }
    fun onPortChange(v: String) = _state.update { it.copy(port = v, error = null) }
    fun onApiKeyChange(v: String) = _state.update { it.copy(apiKey = v, error = null) }

    fun connect() {
        val s = _state.value
        val port = s.port.toIntOrNull() ?: run {
            _state.update { it.copy(error = "Puerto inválido") }
            return
        }
        val connection = ServerConnection(
            host = s.host.trim(),
            port = port,
            apiKey = s.apiKey.trim(),
        )
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            connectionRepo.testConnection(connection).fold(
                onSuccess = {
                    connectionRepo.saveConnection(connection)
                    // Unload previous network module if reconnecting
                    loadedNetworkModule?.let { unloadKoinModules(it) }
                    val module = networkModule(connection)
                    loadedNetworkModule = module
                    loadKoinModules(module)
                    // Ensure opencode is started and managed by the gateway
                    val api = OpenCodeApi(connection)
                    val started = api.startProcess()
                    api.close()
                    android.util.Log.d("OC-Connect", "startProcess result: started=$started")
                    _state.update { it.copy(isLoading = false) }
                    startOrRestartSseService()
                    _navigateToProjects.emit(Unit)
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = "No se pudo conectar: ${e.message}") }
                },
            )
        }
    }
}
