package com.opencode.shared.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderListDto(
    val all: List<ProviderDto> = emptyList(),
    val connected: List<String> = emptyList(),
)

@Serializable
data class ProviderDto(
    val id: String,
    val name: String = "",
    // The API returns models as a JSON object (map), not an array
    val models: Map<String, ModelDto> = emptyMap(),
)

@Serializable
data class ModelDto(
    val id: String = "",
    val name: String = "",
    @SerialName("providerID") val providerID: String = "",
    @SerialName("cost") val cost: ModelCostDto? = null,
    @SerialName("limit") val limit: ModelLimitDto? = null,
)

@Serializable
data class ModelCostDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
)

@Serializable
data class ModelLimitDto(
    val context: Int = 0,
)
