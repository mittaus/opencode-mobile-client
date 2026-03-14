package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.local.PreferencesStorage
import com.opencode.shared.domain.model.ServerConnection
import com.opencode.shared.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectionRepositoryImpl(
    private val storage: PreferencesStorage,
) : ConnectionRepository {

    override fun getConnection(): Flow<ServerConnection?> =
        storage.observeString(KEY_HOST).map { host ->
            if (host == null) null
            else ServerConnection(
                host = host,
                port = storage.getInt(KEY_PORT) ?: 8080,
                apiKey = storage.getString(KEY_API_KEY) ?: "",
            )
        }

    override suspend fun saveConnection(connection: ServerConnection) {
        storage.putString(KEY_HOST, connection.host)
        storage.putInt(KEY_PORT, connection.port)
        storage.putString(KEY_API_KEY, connection.apiKey)
    }

    override suspend fun clearConnection() = storage.clear()

    override suspend fun testConnection(connection: ServerConnection): Result<String> =
        runCatching {
            val api = OpenCodeApi(connection)
            val response = api.gatewayHealth()
            api.close()
            response
        }

    companion object {
        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"
        const val KEY_API_KEY = "server_api_key"
    }
}
