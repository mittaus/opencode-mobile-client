package com.opencode.shared.data.repository

import com.opencode.shared.data.remote.api.OpenCodeApi
import com.opencode.shared.data.remote.mapper.toDomain
import com.opencode.shared.domain.model.Provider
import com.opencode.shared.domain.repository.ProviderRepository

class ProviderRepositoryImpl(private val api: OpenCodeApi) : ProviderRepository {

    override suspend fun getProviders(): Result<List<Provider>> = runCatching {
        api.getProviders().toDomain()
    }.onSuccess { providers ->
        println("[ProviderRepo] getProviders ✓ → ${providers.size} providers, connected=${providers.count { it.isConnected }}, totalModels=${providers.sumOf { it.models.size }}")
    }.onFailure { e ->
        println("[ProviderRepo] getProviders ✗ → ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
}
