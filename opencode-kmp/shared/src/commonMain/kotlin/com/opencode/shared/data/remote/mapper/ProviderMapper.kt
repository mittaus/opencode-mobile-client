package com.opencode.shared.data.remote.mapper

import com.opencode.shared.data.remote.dto.*
import com.opencode.shared.domain.model.*

fun ProviderListDto.toDomain(): List<Provider> = all.map { dto ->
    dto.toDomain(connectedIds = connected)
}

fun ProviderDto.toDomain(connectedIds: List<String>): Provider = Provider(
    id = id,
    name = name.ifBlank { id },
    models = models.values.map { it.toDomain(providerId = id, providerName = name.ifBlank { id }) },
    isConnected = id in connectedIds,
)

fun ModelDto.toDomain(providerId: String, providerName: String): Model = Model(
    id = id.ifBlank { name },
    name = name.ifBlank { id },
    providerId = providerID.ifBlank { providerId },
    providerName = providerName,
    inputCostPerMToken = cost?.input ?: 0.0,
    outputCostPerMToken = cost?.output ?: 0.0,
    contextWindow = limit?.context ?: 0,
)
