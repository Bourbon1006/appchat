package org.example.appchathandler.event

import org.example.appchathandler.model.WebSocketMessage

data class SessionUpdateEvent(
    val userId: Long,
    val message: WebSocketMessage.SessionUpdate
) 