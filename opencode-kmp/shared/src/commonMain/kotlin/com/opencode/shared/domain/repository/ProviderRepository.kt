package com.opencode.shared.domain.repository

import com.opencode.shared.domain.model.Provider

interface ProviderRepository {
    suspend fun getProviders(): Result<List<Provider>>
}
