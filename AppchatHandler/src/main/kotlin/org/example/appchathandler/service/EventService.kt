package org.example.appchathandler.service

import org.example.appchathandler.dto.MessageSessionDTO
import org.example.appchathandler.dto.MessageSessionInfo
import org.example.appchathandler.entity.Message
import org.example.appchathandler.event.SessionUpdateEvent
import org.example.appchathandler.event.SessionsUpdateEvent
import org.example.appchathandler.model.WebSocketMessage
import org.example.appchathandler.websocket.ChatWebSocketHandler
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import org.springframework.context.annotation.Lazy

@Service
class EventService(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun notifySessionsUpdate(userId: Long, sessions: List<MessageSessionDTO>) {
        try {
            val updateMessage = WebSocketMessage.SessionUpdate(
                senderId = userId,
                sessions = sessions
            )
            
            // 发布事件而不是直接调用 WebSocket
            applicationEventPublisher.publishEvent(SessionUpdateEvent(userId, updateMessage))
            
            println("✅ Session update event published for user: $userId")
        } catch (e: Exception) {
            println("❌ Error publishing session update event: ${e.message}")
            e.printStackTrace()
        }
    }

    fun notifyNewMessage(message: Message) {
        // 现有的通知新消息逻辑...
    }
} 