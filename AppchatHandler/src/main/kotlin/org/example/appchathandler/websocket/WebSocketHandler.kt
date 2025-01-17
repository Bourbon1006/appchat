package org.example.appchathandler.websocket

import org.example.appchathandler.dto.WebSocketMessageDTO
import org.example.appchathandler.service.UserService
import org.example.appchathandler.service.MessageService
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

@Component
class WebSocketHandler(
    private val userService: UserService,
    private val messageService: MessageService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<Long, WebSocketSession>()

    init {
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.registerKotlinModule()
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes["userId"] as Long
        sessions[userId] = session
        userService.setUserOnline(userId, true)
        
        // 广播在线用户列表
        broadcastOnlineUsers()
    }

    private fun broadcastOnlineUsers() {
        val onlineUsers = userService.getOnlineUsers()
        val message = WebSocketMessageDTO(
            type = "users",
            users = onlineUsers
        )
        broadcast(message)
    }

    private fun broadcast(message: WebSocketMessageDTO) {
        val json = objectMapper.writeValueAsString(message)
        sessions.values.forEach { it.sendMessage(TextMessage(json)) }
    }

    // ... 其他代码保持不变 ...
} 