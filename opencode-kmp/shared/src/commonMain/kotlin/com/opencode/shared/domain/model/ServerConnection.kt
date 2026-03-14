package com.opencode.shared.domain.model

data class ServerConnection(
    val host: String,
    val port: Int,
    val apiKey: String = "",
) {
    val baseUrl: String get() {
        val trimmed = host.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                trimmed.trimEnd('/')
            port == 443 ->
                "https://$trimmed"
            else ->
                "http://$trimmed:$port"
        }
    }
}
