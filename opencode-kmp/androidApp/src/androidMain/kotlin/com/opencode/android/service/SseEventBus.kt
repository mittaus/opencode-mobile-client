package com.opencode.android.service

import com.opencode.shared.domain.model.ServerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Application-scoped singleton that acts as the single source of truth for SSE events.
 *
 * [OpenCodeSseService] is the only producer — it collects from [ObserveEventsUseCase]
 * inside its own [CoroutineScope] and emits here.
 *
 * [ChatViewModel] and any other consumer subscribe to [events] without starting
 * their own SSE connection, so there is always exactly one upstream connection.
 */
object SseEventBus {
    private val _events = MutableSharedFlow<ServerEvent>(
        replay = 50,
        extraBufferCapacity = 200,
    )

    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    suspend fun emit(event: ServerEvent) {
        _events.emit(event)
    }
}
