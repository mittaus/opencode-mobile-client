package com.opencode.shared.domain.model

data class Provider(
    val id: String,
    val name: String,
    val models: List<Model>,
    val isConnected: Boolean,
)

data class Model(
    val id: String,
    val name: String,
    val providerId: String,
    val providerName: String,
    val inputCostPerMToken: Double = 0.0,
    val outputCostPerMToken: Double = 0.0,
    val contextWindow: Int = 0,
)
