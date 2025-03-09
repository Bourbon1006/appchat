package org.example.appchathandler.service

import org.example.appchathandler.dto.MessageSessionInfo
import org.example.appchathandler.event.SessionsUpdateEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class EventService(private val eventPublisher: ApplicationEventPublisher) {
    fun publishSessionsUpdate(userId: Long, sessions: List<MessageSessionInfo>) {
        eventPublisher.publishEvent(SessionsUpdateEvent(userId, sessions))
    }
} 