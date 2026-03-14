package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.ServerConnection
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getConnection(): Flow<ServerConnection?>
    suspend fun saveConnection(connection: ServerConnection)
    suspend fun clearConnection()
    suspend fun testConnection(connection: ServerConnection): Result<String>
}
