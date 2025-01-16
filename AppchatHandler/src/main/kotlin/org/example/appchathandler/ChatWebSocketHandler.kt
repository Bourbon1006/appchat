package org.example.appchathandler

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import org.example.appchathandler.service.MessageService
import org.example.appchathandler.service.UserService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.JsonNode

@Component
class ChatWebSocketHandler(
    private val messageService: MessageService,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    data class MessageDTO(
        val id: Long,
        val senderId: Long,
        val senderName: String,
        val receiverId: Long?,
        val receiverName: String?,
        val content: String,
        val type: MessageType?,
        val fileUrl: String?,
        val timestamp: LocalDateTime,
        val isRead: Boolean
    )

    data class UserStatusDTO(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatar: String?,
        val online: Boolean
    )

    private fun Message.toDTO() = MessageDTO(
        id = id,
        senderId = sender.id,
        senderName = sender.username,
        receiverId = receiver?.id,
        receiverName = receiver?.username,
        content = content,
        type = type ?: MessageType.TEXT,
        fileUrl = fileUrl,
        timestamp = timestamp,
        isRead = isRead
    )

    private val sessions = ConcurrentHashMap<Long, WebSocketSession>()

    sealed class WebSocketMessage {
        data class ChatMessage(
            val id: Long = 0,
            val senderId: Long,
            val senderName: String,
            val receiverId: Long? = null,
            val receiverName: String? = null,
            val content: String,
            val type: MessageType = MessageType.TEXT,
            val fileUrl: String? = null,
            val timestamp: LocalDateTime = LocalDateTime.now(),
            val isRead: Boolean = false
        ) : WebSocketMessage()

        data class FileTransfer(
            val fileId: String,
            val fileName: String,
            val fileType: String,
            val senderId: Long,
            val receiverId: Long?
        ) : WebSocketMessage()
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
        if (userId != null) {
            sessions[userId] = session
            userService.setUserOnline(userId, true)
            
            val messages = messageService.getMessageHistory(userId)
            val historyResponse = mapOf(
                "type" to "history",
                "messages" to messages.map { it.toDTO() }
            )
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(historyResponse)))

            val onlineUsers = userService.getOnlineUsers().map { user ->
                UserStatusDTO(
                    id = user.id,
                    username = user.username,
                    nickname = user.nickname,
                    avatar = user.avatar,
                    online = user.online
                )
            }
            val usersResponse = mapOf(
                "type" to "users",
                "users" to onlineUsers
            )
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(usersResponse)))

            broadcastUserStatus(userId, true)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val messageNode = objectMapper.readTree(message.payload)
            val messageType = messageNode.get("messageType")?.asText() ?: "CHAT" // 默认为CHAT类型
            
            when (messageType) {
                "CHAT" -> handleChatMessage(messageNode)
                "FILE" -> handleFileTransfer(messageNode)
                else -> {
                    val errorResponse = mapOf(
                        "type" to "error",
                        "message" to "Unsupported message type: $messageType"
                    )
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(errorResponse)))
                }
            }
        } catch (e: Exception) {
            val errorResponse = mapOf(
                "type" to "error",
                "message" to "Failed to process message: ${e.message}"
            )
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(errorResponse)))
        }
    }

    private fun handleChatMessage(messageNode: JsonNode) {
        val chatMessage = objectMapper.treeToValue(messageNode, WebSocketMessage.ChatMessage::class.java)
        val sender = userService.getUser(chatMessage.senderId)
        val receiver = chatMessage.receiverId?.let { userService.getUser(it) }
        
        val savedMessage = messageService.saveMessage(
            Message(
                sender = sender,
                receiver = receiver,
                content = chatMessage.content,
                type = chatMessage.type,
                fileUrl = chatMessage.fileUrl
            )
        )

        broadcastMessage(chatMessage.receiverId, savedMessage)
    }

    private fun handleFileTransfer(messageNode: JsonNode) {
        val fileTransfer = objectMapper.treeToValue(messageNode, WebSocketMessage.FileTransfer::class.java)
        // TODO: 实现文件传输逻辑
        val response = mapOf(
            "type" to "file",
            "fileId" to fileTransfer.fileId,
            "status" to "processing"
        )
        val json = objectMapper.writeValueAsString(response)
        sessions[fileTransfer.senderId]?.sendMessage(TextMessage(json))
    }

    private fun broadcastMessage(receiverId: Long?, message: Message) {
        val response = mapOf(
            "type" to "message",
            "message" to message.toDTO()
        )
        val messageJson = objectMapper.writeValueAsString(response)

        if (receiverId == null) {
            sessions.values.forEach { it.sendMessage(TextMessage(messageJson)) }
        } else {
            sessions[receiverId]?.sendMessage(TextMessage(messageJson))
            sessions[message.sender.id]?.sendMessage(TextMessage(messageJson))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
        if (userId != null) {
            sessions.remove(userId)
            userService.setUserOnline(userId, false)
            broadcastUserStatus(userId, false)
        }
    }

    private fun broadcastUserStatus(userId: Long, online: Boolean) {
        val user = userService.getUser(userId)
        val statusUpdate = UserStatusDTO(
            id = user.id,
            username = user.username,
            nickname = user.nickname,
            avatar = user.avatar,
            online = online
        )
        val response = mapOf(
            "type" to "userStatus",
            "user" to statusUpdate
        )
        val statusJson = objectMapper.writeValueAsString(response)
        sessions.values.forEach { it.sendMessage(TextMessage(statusJson)) }
    }
} 