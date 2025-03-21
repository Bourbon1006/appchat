package org.example.appchathandler.model

import org.example.appchathandler.dto.MessageSessionDTO
import java.time.LocalDateTime

sealed class WebSocketMessage {
    data class ChatMessage(
        val type: Type = Type.MESSAGE,
        val senderId: Long,
        val content: String?,
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val sessions: List<MessageSessionDTO>? = null
    ) : WebSocketMessage()

    data class SessionUpdate(
        val type: Type = Type.SESSION_UPDATE,
        val senderId: Long,
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val sessions: List<MessageSessionDTO>
    ) : WebSocketMessage()

    enum class Type {
        MESSAGE,
        SESSION_UPDATE,
        FRIEND_REQUEST,
        FRIEND_ACCEPTED,
        FRIEND_DELETED,
        STATUS_CHANGED
    }
} 