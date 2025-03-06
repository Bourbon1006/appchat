package org.example.appchathandler.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.service.MessageService
import org.example.appchathandler.service.UserService
import org.example.appchathandler.service.FriendRequestService
import org.example.appchathandler.service.GroupService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.JsonNode
import org.example.appchathandler.dto.WebSocketMessageDTO
import org.example.appchathandler.entity.User
import org.example.appchathandler.dto.MessageDTO
import com.fasterxml.jackson.core.type.TypeReference

@Component
class ChatWebSocketHandler(
    private val messageService: MessageService,
    private val userService: UserService,
    private val friendRequestService: FriendRequestService,
    private val objectMapper: ObjectMapper,
    private val groupService: GroupService
) : TextWebSocketHandler() {

    data class UserStatusDTO(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatarUrl: String?,
        val isOnline: Boolean
    )

    data class FriendRequestDTO(
        val id: Long,
        val sender: UserStatusDTO,
        val receiver: UserStatusDTO,
        val status: String,
        val timestamp: LocalDateTime
    )
    private fun User.toStatusDTO() = UserStatusDTO(
        id = id,
        username = username,
        nickname = nickname,
        avatarUrl = avatarUrl,
        isOnline = isOnline
    )
    private fun Message.toDTO() = MessageDTO(
        id = id,
        content = content,
        timestamp = timestamp,
        senderId = sender.id,
        senderName = sender.username,
        receiverId = receiver?.id,
        receiverName = receiver?.username,
        groupId = group?.id,
        type = type,
        fileUrl = fileUrl
    )
    private fun FriendRequest.toDTO() = FriendRequestDTO(
        id = id,
        sender = sender.toStatusDTO(),
        receiver = receiver.toStatusDTO(),
        status = status.name,
        timestamp = timestamp
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
            val type: org.example.appchathandler.entity.MessageType = org.example.appchathandler.entity.MessageType.TEXT,
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
    private fun JsonNode.asLongOrNull(): Long? {
        return if (this.isNull) null else this.asLong()
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
                            avatarUrl = onlineUser.avatarUrl,
                            isOnline = true  // 确保在线状态正确
                        )
                    }
                
                // 发送在线用户列表
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "users",
                    "users" to onlineUsers
                ))))
                val userInfo = mapOf(
                    "id" to user.id,
                    "username" to user.username,
                    "nickname" to user.nickname,
                    "avatarUrl" to user.avatarUrl,
                    "isOnline" to user.isOnline
                )
                sessions.values.forEach { s ->
                    if (s != session) {
                        s.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "userStatus",
                            "user" to userInfo
                        ))))
                    }
                }
                // 发送待处理的好友请求
                val pendingRequests = friendRequestService.getPendingRequests(userId)
                if (pendingRequests.isNotEmpty()) {
                    pendingRequests.forEach { request ->
                        session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "friendRequest",
                            "friendRequest" to request.toDTO()
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
        val msg: Map<String, Any> = objectMapper.readValue(message.payload, 
            object : TypeReference<Map<String, Any>>() {})
            
        when (msg["type"] as String) {
            "CHAT" -> {
                val messageType = MessageType.valueOf(
                    msg["messageType"] as? String ?: "TEXT"
                )
                val content = msg["content"] as String
                val senderId = (msg["senderId"] as Number).toLong()
                val senderName = msg["senderName"] as? String
                val receiverId = (msg["receiverId"] as? Number)?.toLong()
                val groupId = (msg["groupId"] as? Number)?.toLong()
                val fileUrl = msg["fileUrl"] as? String
                // 根据消息是否包含 groupId 来区分私聊和群聊
                if (groupId != null) {
                    handleGroupMessage(
                        content = content,
                        senderId = senderId,
                        groupId = groupId,
                        type = messageType,
                        fileUrl = fileUrl,
                        session = session
                    )
                } else {
                    handlePrivateMessage(
                        content = content,
                        senderId = senderId,
                        receiverId = receiverId,
                        type = messageType,
                        fileUrl = fileUrl,
                        session = session
                    )
                }
            }
            "FRIEND_REQUEST" -> handleFriendRequest(msg, session)
            "CREATE_GROUP" -> handleCreateGroup(msg, session)
            "HANDLE_FRIEND_REQUEST" -> {
                val requestId = msg["requestId"] as Number
                val accept = msg["accept"] as Boolean
                val request = friendRequestService.handleFriendRequest(requestId.toLong(), accept)
                // 通知发送者请求结果
                sessions[request.sender.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "friendRequestResult",
                    "friendRequest" to request
                ))))
            }
            "history" -> {
                val messages = msg["messages"] as? List<Map<String, Any>>
                messages?.forEach { message ->
                    val savedMessage = messageService.createMessage(
                        content = message["content"] as String,
                        senderId = message["senderId"] as Long,
                        receiverId = message["receiverId"] as? Long,
                        type = MessageType.valueOf(message["type"] as String),
                        fileUrl = message["fileUrl"] as? String
                    )
                    val dto = savedMessage.toDTO()
                    sessions[savedMessage.sender.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                        WebSocketMessageDTO(
                            type = "message",
                            message = dto
                        )
                    )))
                }
            }
            else -> {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "error",
                    "message" to "Unsupported message type: ${msg["type"]}"
                ))))
            }
        }
    }
    private fun handleGroupMessage(
        content: String,
        senderId: Long,
        groupId: Long,
        type: MessageType,
        fileUrl: String?,
        session: WebSocketSession
    ) {
        try {
            val savedMessage = messageService.createMessage(
                content = content,
                senderId = senderId,
                groupId = groupId,
                type = type,
                fileUrl = fileUrl
            )
            // 获取群组成员并发送消息
            val group = groupService.getGroup(groupId)
            group.members.forEach { member ->
                sessions[member.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                    WebSocketMessageDTO(
                        type = "message",
                        message = savedMessage.toDTO()
                    )
                )))
            }
        } catch (e: Exception) {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                WebSocketMessageDTO(
                    type = "error",
                    error = e.message
                )
            )))
        }
    }
    private fun handlePrivateMessage(
        content: String,
        senderId: Long,
        receiverId: Long?,
        type: MessageType,
        fileUrl: String?,
        session: WebSocketSession
    ) {
        try {
            val savedMessage = messageService.createMessage(
                content = content,
                senderId = senderId,
                receiverId = receiverId,
                type = type,
                fileUrl = fileUrl
            )
            // 发送给接收者
            receiverId?.let { id ->
                println("Sending message to receiver with ID: $id")
                sessions[id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                    WebSocketMessageDTO(
                        type = "message",
                        message = savedMessage.toDTO()
                    )
                )))
            }
            // 发送给发送者（确认消息已发送）
            sessions[senderId]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                WebSocketMessageDTO(
                    type = "message",
                    message = savedMessage.toDTO()
                )
            )))
        } catch (e: Exception) {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                WebSocketMessageDTO(
                    type = "error",
                    error = e.message
                )
            )))
        }
    }
    private fun handleFriendRequest(message: Map<String, Any>, session: WebSocketSession) {
        try {
            println("⭐ Processing friend request with message: $message")
            val senderId = message["senderId"] as Number
            val receiverId = message["receiverId"] as Number
            println("✉️ Sending friend request from user $senderId to user $receiverId")
            val request = friendRequestService.sendFriendRequest(senderId.toLong(), receiverId.toLong())
            println("✅ Friend request created successfully: $request")
            // 通知接收者
            // 无论接收者是否在线，都保存好友请求到数据库
            // 如果接收者在线，立即发送通知
            sessions[receiverId.toLong()]?.let { receiverSession ->
                println("📨 Sending notification to receiver (userId: $receiverId)")
                receiverSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to request.toDTO()
                ))))
                println("✅ Notification sent to receiver successfully")
            }
            // 如果接收者离线，请求会保存在数据库中，等待用户上线时通过getFriendRequests API获取
            // 通知发送者请求已发送
            sessions[senderId.toLong()]?.let { senderSession ->
                println("📤 Sending confirmation to sender (userId: $senderId)")
                senderSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "friendRequestSent",
                    "friendRequest" to request.toDTO()
                ))))
                println("✅ Confirmation sent to sender successfully")
            } ?: println("⚠️ Sender (userId: $senderId) is not online")
        } catch (e: Exception) {
            println("❌ Error handling friend request: ${e.message}")
            e.printStackTrace()
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "message" to e.message
            ))))
        }
    }
    private fun handleCreateGroup(message: Map<String, Any>, session: WebSocketSession) {
        val name = message["name"] as String
        val creatorId = message["creatorId"] as Number
        val memberIds = message["memberIds"] as List<Number>
        try {
            val groupDto = groupService.createGroup(name, creatorId.toLong(), memberIds.map { it.toLong() })
            // 通知所有群成员
            groupDto.members.forEach { member ->
                sessions[member.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                    WebSocketMessageDTO(
                        type = "groupCreated",
                        groupDTO = groupDto  // 使用 GroupDTO
                    )
                )))
            }
        } catch (e: Exception) {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                WebSocketMessageDTO(
                    type = "error",
                    error = e.message
                )
            )))
        }
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
                avatarUrl = user.avatarUrl,
                isOnline = false
            )
            val statusJson = objectMapper.writeValueAsString(mapOf(
                "type" to "userStatus",
                "user" to offlineStatus
            ))
            sessions.values.forEach { it.sendMessage(TextMessage(statusJson)) }
        }
    }
}