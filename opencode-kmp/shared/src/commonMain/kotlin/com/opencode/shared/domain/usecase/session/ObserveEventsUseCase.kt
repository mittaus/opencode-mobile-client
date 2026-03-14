package com.opencode.shared.domain.usecase.session

import com.opencode.shared.domain.model.ServerEvent
import com.opencode.shared.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class ObserveEventsUseCase(private val repo: SessionRepository) {
    operator fun invoke(): Flow<ServerEvent> = repo.observeEvents()
}
