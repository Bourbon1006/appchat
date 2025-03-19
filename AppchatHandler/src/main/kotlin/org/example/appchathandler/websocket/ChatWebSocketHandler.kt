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
import org.springframework.context.event.EventListener
import org.example.appchathandler.event.SessionsUpdateEvent
import org.example.appchathandler.event.FriendRequestEvent
import org.json.JSONObject
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.dto.toStatusDTO
import com.fasterxml.jackson.core.type.TypeReference
import org.example.appchathandler.event.SessionUpdateEvent

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

    private val sessions = ConcurrentHashMap<Long, WebSocketSession>()
    private val sessionLocks = ConcurrentHashMap<Long, Any>()

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
                
                // 添加日志
                println("🔌 WebSocket connection established for user $userId")
                println("🔌 Current active sessions: ${sessions.keys}")
            } catch (e: Exception) {
                e.printStackTrace()
                session.close()
            }
        }
    }
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val jsonNode = objectMapper.readTree(message.payload)
            val type = jsonNode.get("type").asText()

            when (type) {
                "message" -> {
                    val messageData = objectMapper.treeToValue(jsonNode, Map::class.java) as Map<String, Any>
                    handleChatMessage(messageData, session)
                }
                "CHAT" -> {
                    val messageType = MessageType.valueOf(
                        jsonNode["messageType"]?.asText() ?: "TEXT"
                    )
                    val content = jsonNode["content"]?.asText() ?: ""
                    val senderId = jsonNode["senderId"]?.asLong() ?: 0
                    val senderName = jsonNode["senderName"]?.asText()
                    val receiverId = jsonNode["receiverId"]?.asLong()
                    val groupId = jsonNode["groupId"]?.asLong()
                    val fileUrl = jsonNode["fileUrl"]?.asText()
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
                "FRIEND_REQUEST" -> handleFriendRequest(jsonNode, session)
                "CREATE_GROUP" -> handleCreateGroup(jsonNode, session)
                "HANDLE_FRIEND_REQUEST" -> {
                    val requestId = jsonNode["requestId"]?.asLong() ?: throw IllegalArgumentException("requestId is required")
                    val accept = jsonNode["accept"]?.asBoolean() ?: false
                    val request = friendRequestService.handleFriendRequest(requestId, accept)
                    // 通知发送者请求结果
                    sessions[request.sender.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "friendRequestResult",
                        "friendRequest" to request.toDTO()
                    ))))
                }
                "history" -> {
                    val messages = jsonNode["messages"] as? List<Map<String, Any>>
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
                        "message" to "Unsupported message type: $type"
                    ))))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "message" to e.message
            ))))
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
    private fun handleFriendRequest(message: JsonNode, session: WebSocketSession) {
        try {
            println("⭐ Processing friend request with message: $message")
            val senderId = message["senderId"]?.asLong() ?: 0
            val receiverId = message["receiverId"]?.asLong() ?: 0
            println("✉️ Sending friend request from user $senderId to user $receiverId")
            val request = friendRequestService.sendFriendRequest(senderId, receiverId)
            println("✅ Friend request created successfully: $request")
            // 通知接收者
            // 无论接收者是否在线，都保存好友请求到数据库
            // 如果接收者在线，立即发送通知
            sessions[receiverId]?.let { receiverSession ->
                println("📨 Sending notification to receiver (userId: $receiverId)")
                receiverSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to request.toDTO()
                ))))
                println("✅ Notification sent to receiver successfully")
            }
            // 如果接收者离线，请求会保存在数据库中，等待用户上线时通过getFriendRequests API获取
            // 通知发送者请求已发送
            sessions[senderId]?.let { senderSession ->
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
    private fun handleCreateGroup(message: JsonNode, session: WebSocketSession) {
        val name = message["name"]?.asText() ?: ""
        val creatorId = message["creatorId"]?.asLong() ?: 0
        val memberIds = message["memberIds"] as? List<Number> ?: emptyList()
        try {
            val groupDto = groupService.createGroup(name, creatorId, memberIds.map { it.toLong() })
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
            sessionLocks.remove(userId)  // 清理锁对象
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

    fun sendMessageToUser(userId: Long, message: Map<String, Any>) {
        val session = sessions[userId]
        if (session != null && session.isOpen) {
            // 获取或创建该用户的锁对象
            val lock = sessionLocks.computeIfAbsent(userId) { Any() }
            
            // 使用同步块确保同一时间只有一个线程向该用户发送消息
            synchronized(lock) {
                try {
                    val messageJson = objectMapper.writeValueAsString(message)
                    session.sendMessage(TextMessage(messageJson))
                    println("📤 Sent message to user $userId: $messageJson")
                } catch (e: Exception) {
                    println("❌ Error sending message to user $userId: ${e.message}")
                    e.printStackTrace()
                }
            }
        } else {
            println("⚠️ No active session found for user $userId")
        }
    }

    @EventListener
    fun handleSessionsUpdateEvent(event: SessionsUpdateEvent) {
        sendMessageToUser(event.userId, mapOf(
            "type" to "sessions_update",
            "sessions" to event.sessions
        ))
    }

    @EventListener
    fun handleFriendRequestEvent(event: FriendRequestEvent) {
        println("🔔 Received FriendRequestEvent for request ${event.friendRequest.id} from ${event.friendRequest.sender.username} to ${event.friendRequest.receiver.username}")
        sendFriendRequest(event.friendRequest)
    }
    
    fun sendFriendRequest(request: FriendRequest) {
        val receiverId = request.receiver.id
        println("🔍 Attempting to send friend request to user $receiverId")
        println("🔍 Active sessions: ${sessions.keys}")
        
        val session = sessions[receiverId]
        
        if (session != null && session.isOpen) {
            try {
                // 使用 DTO 对象而不是直接使用实体对象
                val friendRequestDTO = request.toDTO()
                
                val message = mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to friendRequestDTO
                )
                val messageJson = objectMapper.writeValueAsString(message)
                println("📝 Friend request message: $messageJson")
                session.sendMessage(TextMessage(messageJson))
                println("📤 Sent friend request notification to user $receiverId")
            } catch (e: Exception) {
                println("❌ Error sending friend request notification: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠️ No active session found for user $receiverId (session=${session}, isOpen=${session?.isOpen})")
        }
    }

    fun notifyFriendDeleted(userId: Long, deletedFriendId: Long) {
        val session = sessions[userId] ?: return
        
        val message = JSONObject().apply {
            put("type", "friendDeleted")
            put("friendId", deletedFriendId)
        }
        
        session.sendMessage(TextMessage(message.toString()))
    }

    fun sendToUser(userId: Long, message: org.example.appchathandler.model.WebSocketMessage) {
        try {
            val session = sessions[userId]
            if (session != null && session.isOpen) {
                val messageJson = objectMapper.writeValueAsString(message)
                session.sendMessage(TextMessage(messageJson))
                println("✅ Message sent to user $userId: $messageJson")
            } else {
                println("⚠️ User $userId is not connected")
            }
        } catch (e: Exception) {
            println("❌ Error sending message to user $userId: ${e.message}")
            e.printStackTrace()
        }
    }

    fun sendToUsers(userIds: Set<Long>, message: org.example.appchathandler.model.WebSocketMessage) {
        userIds.forEach { userId ->
            sendToUser(userId, message)
        }
    }

    private fun handleChatMessage(messageData: Map<String, Any>, session: WebSocketSession) {
        try {
            val messageType = MessageType.valueOf(messageData["messageType"] as? String ?: "TEXT")
            val content = messageData["content"] as String
            val senderId = (messageData["senderId"] as Number).toLong()
            val receiverId = (messageData["receiverId"] as? Number)?.toLong()
            val groupId = (messageData["groupId"] as? Number)?.toLong()
            val fileUrl = messageData["fileUrl"] as? String

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
        } catch (e: Exception) {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                WebSocketMessageDTO(
                    type = "error",
                    error = e.message
                )
            )))
        }
    }

    @EventListener
    fun handleSessionUpdateEvent(event: SessionUpdateEvent) {
        sendToUser(event.userId, event.message)
    }
}