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
            try {
                // 先获取用户信息
                val user = userService.getUser(userId)
                
                // 设置用户在线状态
                userService.setUserOnline(userId, true)
                
                // 保存会话
                sessions[userId] = session
                
                // 获取在线用户列表（排除自己）
                val onlineUsers = userService.getOnlineUsers()
                    .filter { it.id != userId }
                    .map { onlineUser ->
                        UserStatusDTO(
                            id = onlineUser.id,
                            username = onlineUser.username,
                            nickname = onlineUser.nickname,
                            avatar = onlineUser.avatar,
                            online = true  // 确保在线状态正确
                        )
                    }
                
                // 发送在线用户列表
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "users",
                    "users" to onlineUsers
                ))))

                // 广播新用户上线通知给其他用户
                val newUserStatus = UserStatusDTO(
                    id = user.id,
                    username = user.username,
                    nickname = user.nickname,
                    avatar = user.avatar,
                    online = true
                )
                
                sessions.values.forEach { s ->
                    if (s != session) {
                        s.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "userStatus",
                            "user" to newUserStatus
                        ))))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                session.close()
            }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            println("Received message: ${message.payload}")  // 添加日志
            val messageNode = objectMapper.readTree(message.payload)
            val messageType = messageNode.get("type")?.asText() ?: "CHAT"
            
            when (messageType) {
                "CHAT" -> {
                    val senderId = messageNode.get("senderId").asLong()
                    val senderName = messageNode.get("senderName")?.asText()
                    val content = messageNode.get("content").asText()
                    val receiverId = messageNode.get("receiverId")?.asLong()
                    
                    val sender = userService.getUser(senderId)
                    val receiver = receiverId?.let { userService.getUser(it) }
                    
                    val savedMessage = messageService.saveMessage(
                        Message(
                            sender = sender,
                            receiver = receiver,
                            content = content,
                            type = MessageType.TEXT
                        )
                    )

                    // 发送给接收者
                    receiverId?.let { rid ->
                        sessions[rid]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "message",
                            "message" to savedMessage.toDTO()
                        ))))
                    }

                    // 发送给发送者（确认消息已发送）
                    sessions[senderId]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "message",
                        "message" to savedMessage.toDTO()
                    ))))
                }
                "FILE" -> handleFileTransfer(messageNode)
                else -> {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "error",
                        "message" to "Unsupported message type: $messageType"
                    ))))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error message payload: ${message.payload}")  // 添加错误消息日志
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "message" to "Failed to process message: ${e.message}"
            ))))
        }
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

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.uri?.query?.substringAfter("userId=")?.toLongOrNull()
        if (userId != null) {
            sessions.remove(userId)
            userService.setUserOnline(userId, false)
            
            // 广播用户下线通知
            val user = userService.getUser(userId)
            val offlineStatus = UserStatusDTO(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                avatar = user.avatar,
                online = false
            )
            
            val statusJson = objectMapper.writeValueAsString(mapOf(
                "type" to "userStatus",
                "user" to offlineStatus
            ))
            
            sessions.values.forEach { it.sendMessage(TextMessage(statusJson)) }
        }
    }
} 