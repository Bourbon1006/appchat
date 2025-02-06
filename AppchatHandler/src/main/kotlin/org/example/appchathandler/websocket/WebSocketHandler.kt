package org.example.appchathandler.websocket

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.WebSocketMessageDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.entity.User
import org.example.appchathandler.service.UserService
import org.example.appchathandler.service.MessageService
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.CloseStatus
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.LocalDateTime
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.appchathandler.service.GroupService

@Component
class WebSocketHandler(
    private val userService: UserService,
    private val messageService: MessageService,
    private val groupService: GroupService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<Long, WebSocketSession>()

    data class UserStatusMessage(
        val type: String = "userStatus",
        val user: UserStatusDTO
    )

    data class UserStatusDTO(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatarUrl: String?,
        val isOnline: Boolean
    )

    private fun User.toStatusDTO() = UserStatusDTO(
        id = id,
        username = username,
        nickname = nickname,
        avatarUrl = avatarUrl,
        isOnline = isOnline
    )

    init {
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.registerKotlinModule()
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
        if (userId != null) {
            val user = userService.getUser(userId)
            userService.setUserOnline(userId, true)
            sessions[userId] = session

            // 广播用户上线状态
            broadcastUserStatus(user.toStatusDTO())
            // 发送在线用户列表
            sendOnlineUsers(session)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
        if (userId != null) {
            val user = userService.getUser(userId)
            userService.setUserOnline(userId, false)
            sessions.remove(userId)

            // 广播用户下线状态
            broadcastUserStatus(user.toStatusDTO().copy(isOnline = false))
        }
    }

    private fun broadcastUserStatus(userStatus: UserStatusDTO) {
        val message = UserStatusMessage("userStatus", userStatus)
        val messageJson = objectMapper.writeValueAsString(message)
        sessions.values.forEach { session ->
            session.sendMessage(TextMessage(messageJson))
        }
    }

    private fun broadcastOnlineUsers() {
        val onlineUsers = userService.getOnlineUsers()
        val userStatusDTOs = onlineUsers.map { user -> 
            UserStatusDTO(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                avatarUrl = user.avatarUrl,
                isOnline = user.isOnline
            )
        }
        val message = WebSocketMessageDTO(
            type = "users",
            users = userStatusDTOs
        )
        broadcast(message)
    }

    private fun broadcast(message: WebSocketMessageDTO) {
        val json = objectMapper.writeValueAsString(message)
        sessions.values.forEach { it.sendMessage(TextMessage(json)) }
    }

    private fun getCurrentUserId(session: WebSocketSession): Long {
        return session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
            ?: throw IllegalStateException("No user ID found in session")
    }

    private fun sendOnlineUsers(session: WebSocketSession) {
        val currentUserId = getCurrentUserId(session)
        val onlineUsers = userService.getOnlineUsers()
            .filter { it.id != currentUserId }
        
        val userStatusDTOs = onlineUsers.map { user ->
            UserStatusDTO(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                avatarUrl = user.avatarUrl,
                isOnline = user.isOnline
            )
        }

        session.sendMessage(TextMessage(objectMapper.writeValueAsString(
            WebSocketMessageDTO(
                type = "users",
                users = userStatusDTOs
            )
        )))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            println("⭐ Received WebSocket message: ${message.payload}")
            val messageMap = objectMapper.readValue<Map<String, Any>>(message.payload)
            
            when (messageMap["type"]) {
                "CHAT" -> handleChatMessage(messageMap, session)
                // ... 其他消息类型处理
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error handling WebSocket message: ${e.message}")
        }
    }

    private fun handleChatMessage(messageMap: Map<String, Any>, session: WebSocketSession) {
        try {
            println("⭐ Handling chat message: $messageMap")
            
            val senderId = (messageMap["senderId"] as Number).toLong()
            val sender = userService.getUser(senderId)
            val content = messageMap["content"] as String
            val messageType = MessageType.valueOf(messageMap["messageType"] as String)
            val fileUrl = messageMap["fileUrl"] as? String
            
            val message = Message(
                sender = sender,
                content = content,
                type = messageType,
                fileUrl = fileUrl
            )

            // 处理群聊或私聊
            val groupId = messageMap["groupId"]?.toString()?.toLongOrNull()
            val receiverId = messageMap["receiverId"]?.toString()?.toLongOrNull()

            val savedMessage = when {
                groupId != null -> {
                    println("✅ Saving group message: groupId=$groupId")
                    messageService.saveGroupMessage(message, groupId)
                }
                receiverId != null -> {
                    println("✅ Saving private message: receiverId=$receiverId")
                    messageService.savePrivateMessage(message, receiverId)
                }
                else -> null
            }

            if (savedMessage != null) {
                // 广播消息给相关用户
                broadcastMessage(messageMap, session)
                println("✅ Message processed successfully")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error handling chat message: ${e.message}")
        }
    }

    private fun broadcastMessage(messageMap: Map<String, Any>, session: WebSocketSession) {
        val json = objectMapper.writeValueAsString(messageMap)
        
        val groupId = messageMap["groupId"]?.toString()?.toLongOrNull()
        val receiverId = messageMap["receiverId"]?.toString()?.toLongOrNull()
        val senderId = (messageMap["senderId"] as Number).toLong()

        when {
            groupId != null -> {
                val members = groupService.getGroupMembers(groupId)
                members.forEach { member ->
                    sessions[member.id]?.let { memberSession ->
                        memberSession.sendMessage(TextMessage(json))
                    }
                }
            }
            receiverId != null -> {
                sessions[receiverId]?.sendMessage(TextMessage(json))
                if (session.id != sessions[senderId]?.id) {
                    sessions[senderId]?.sendMessage(TextMessage(json))
                }
            }
        }
    }
} 