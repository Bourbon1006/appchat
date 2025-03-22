package org.example.appchathandler.websocket

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.context.event.EventListener
import org.example.appchathandler.event.SessionsUpdateEvent
import org.example.appchathandler.event.FriendRequestEvent
import org.json.JSONObject
import com.fasterxml.jackson.core.type.TypeReference
import org.example.appchathandler.dto.*
import org.example.appchathandler.entity.*
import org.example.appchathandler.event.SessionUpdateEvent
import org.example.appchathandler.service.FriendService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.example.appchathandler.event.UserStatusUpdateEvent
import org.example.appchathandler.event.FriendRequestNotificationEvent
import org.example.appchathandler.dto.GroupCreateRequest

@Component
class ChatWebSocketHandler(
    private val messageService: MessageService,
    private val userService: UserService,
    private val friendRequestService: FriendRequestService,
    private val objectMapper: ObjectMapper,
    private val groupService: GroupService,
    private val friendService: FriendService
) : TextWebSocketHandler() {

    data class UserStatusDTO(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatarUrl: String?,
        val onlineStatus: Int,
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
    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

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
                userService.setUserOnline(userId, 1)  // 1 表示在线
                
                // 保存会话
                sessions[userId] = session
                
                // 获取并发送所有待处理的好友请求
                val pendingRequests = friendRequestService.getPendingRequests(userId)
                if (pendingRequests.isNotEmpty()) {
                    println("📬 Found ${pendingRequests.size} pending friend requests for user $userId")
                    pendingRequests.forEach { request ->
                        println("📨 Sending pending friend request from ${request.sender.username}")
                        val message = mapOf(
                            "type" to "FRIEND_REQUEST",
                            "senderId" to request.sender.id,
                            "senderName" to request.sender.username,
                            "message" to "${request.sender.username} 请求添加您为好友",
                            "requestId" to request.id
                        )
                        session.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
                    }
                } else {
                    println("📭 No pending friend requests for user $userId")
                }
                
                // 获取在线用户列表（排除自己）
                val onlineUsers = userService.getOnlineUsers()
                    .filter { it.id != userId }
                    .map { onlineUser ->
                        onlineUser.onlineStatus?.let {
                            UserStatusDTO(
                                id = onlineUser.id,
                                username = onlineUser.username,
                                nickname = onlineUser.nickname,
                                avatarUrl = onlineUser.avatarUrl,
                                onlineStatus = it,
                                isOnline = onlineUser.onlineStatus > 0
                            )
                        }
                    }
                
                // 发送在线用户列表
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "users",
                    "users" to onlineUsers
                ))))

                // 广播用户上线状态
                val userInfo = mapOf(
                    "id" to user.id,
                    "username" to user.username,
                    "nickname" to user.nickname,
                    "avatarUrl" to user.avatarUrl,
                    "onlineStatus" to user.onlineStatus,
                    "isOnline" to (user.onlineStatus > 0)
                )
                sessions.values.forEach { s ->
                    if (s != session) {
                        s.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "userStatus",
                            "user" to userInfo
                        ))))
                    }
                }
                
                // 添加日志
                println("🔌 WebSocket connection established for user $userId")
                println("🔌 Current active sessions: ${sessions.keys}")
            } catch (e: Exception) {
                println("❌ Error in afterConnectionEstablished: ${e.message}")
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
                "FRIEND_REQUEST" -> handleFriendRequest(
                    senderId = jsonNode.get("senderId").asLong(),
                    receiverId = jsonNode.get("receiverId").asLong(),
                    session = session
                )
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
            val message = messageService.createMessage(
                content = content,
                senderId = senderId,
                groupId = groupId,
                type = type,
                fileUrl = fileUrl
            )
            
            // 获取群组所有成员ID并发送消息
            val memberIds = getUsersInGroup(groupId)
            
            for (memberId in memberIds) {
                val userSession = sessions[memberId]
                if (userSession != null && userSession.isOpen) {
                    val dto = messageService.convertToMessageDTO(message)
                    userSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "message",
                        "message" to dto
                    ))))
                }
            }
            
            // 发送响应给发送者
            val responseDto = messageService.convertToMessageDTO(message)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "messageSent",
                "message" to responseDto
            ))))
            
        } catch (e: Exception) {
            logger.error("Error sending group message: ${e.message}", e)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "error" to "Failed to send message: ${e.message}"
            ))))
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
    private fun handleFriendRequest(senderId: Long, receiverId: Long, session: WebSocketSession) {
        try {
            // 1. 保存好友请求到数据库（通过事件处理通知）
            val request = friendRequestService.sendFriendRequest(senderId, receiverId)
            
            // 2. 只通知发送者请求已发送
            val responseMessage = mapOf(
                "type" to "FRIEND_REQUEST_SENT",
                "success" to true
            )
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(responseMessage)))

        } catch (e: Exception) {
            println("❌ Error handling friend request: ${e.message}")
            e.printStackTrace()
            
            val errorMessage = mapOf(
                "type" to "FRIEND_REQUEST_SENT",
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            )
            
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(errorMessage)))
        }
    }
    private fun handleCreateGroup(message: JsonNode, session: WebSocketSession) {
        val name = message["name"]?.asText() ?: ""
        val creatorId = message["creatorId"]?.asLong() ?: 0
        val memberIds = message["memberIds"] as? List<Number> ?: emptyList()
        try {
            val groupDto = groupService.createGroup(
                GroupCreateRequest(
                    name = name,
                    creatorId = creatorId,
                    memberIds = memberIds.map { it.toLong() }
                )
            )
            // 通知所有群成员
            val groupMembers = groupService.getGroupMembers(groupDto.id)
            for (member in groupMembers) {
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
            sessionLocks.remove(userId)
            userService.setUserOnline(userId, 0)  // 0 表示离线
            
            // 广播用户下线通知
            val user = userService.getUser(userId)
            val offlineStatus = UserStatusDTO(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                avatarUrl = user.avatarUrl,
                onlineStatus = 0,
                isOnline = false
            )
            val statusJson = objectMapper.writeValueAsString(mapOf(
                "type" to "userStatus",
                "user" to offlineStatus
            ))
            
            sessions.values.forEach { s ->
                if (s.isOpen) {
                    s.sendMessage(TextMessage(statusJson))
                }
            }
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
        val request = event.friendRequest
        val receiverSession = sessions[request.receiver.id]
        
        if (receiverSession?.isOpen == true) {
            try {
                // 第一种格式的消息（详细信息）
                val detailedMessage = mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to request
                )
                receiverSession.sendMessage(TextMessage(objectMapper.writeValueAsString(detailedMessage)))
                
                // 删除第二种格式的消息，只保留一种
                /*
                // 第二种格式的消息（简化版）
                val simpleMessage = mapOf(
                    "type" to "FRIEND_REQUEST",
                    "senderId" to request.sender.id,
                    "senderName" to request.sender.username,
                    "message" to "${request.sender.username} 请求添加您为好友",
                    "requestId" to request.id
                )
                receiverSession.sendMessage(TextMessage(objectMapper.writeValueAsString(simpleMessage)))
                */
            } catch (e: Exception) {
                logger.error("Failed to send friend request notification", e)
            }
        }
    }
    
    fun sendFriendRequest(request: FriendRequest) {
        val receiverId = request.receiver.id
        println("🔍 Attempting to send friend request to user $receiverId")
        
        val session = sessions[receiverId]
        if (session != null && session.isOpen) {
            try {
                val friendRequestDTO = request.toDTO()
                val message = mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to friendRequestDTO
                )
                val messageJson = objectMapper.writeValueAsString(message)
                println("📝 Sending friend request: $messageJson")
                session.sendMessage(TextMessage(messageJson))
                println("✅ Friend request sent successfully to user $receiverId")
            } catch (e: Exception) {
                println("❌ Error sending friend request: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("📫 User $receiverId is offline, request will be sent when they reconnect")
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

    fun notifyFriendRequestResult(request: FriendRequest) {
        println("📢 通知好友请求结果: requestId=${request.id}, status=${request.status}")
        
        val senderSession = sessions[request.sender.id]
        if (senderSession != null && senderSession.isOpen) {
            val message = mapOf(
                "type" to "FRIEND_REQUEST_RESULT",
                "requestId" to request.id,
                "status" to request.status.toString(),
                "accepted" to (request.status.toString() == "ACCEPTED")
            )
            try {
                senderSession.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
                println("✅ 已通知发送者 ${request.sender.username}")
            } catch (e: Exception) {
                println("❌ 通知发送者失败: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠️ 发送者 ${request.sender.username} 不在线")
        }
    }

    @EventListener
    fun handleUserStatusUpdate(event: UserStatusUpdateEvent) {
        val user = userService.getUser(event.userId)
        val message = mapOf<String, Any>(
            "type" to "STATUS_CHANGED",
            "data" to mapOf(
                "userId" to event.userId,
                "status" to event.status
            )
        )
        
        // 通知所有在线用户
        sessions.values.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
                } catch (e: Exception) {
                    logger.error("Failed to send status update", e)
                }
            }
        }
    }

    @EventListener
    fun handleFriendRequestNotification(event: FriendRequestNotificationEvent) {
        val request = event.friendRequest
        val receiverId = request.receiver.id
        
        val session = sessions[receiverId]
        if (session != null && session.isOpen) {
            try {
                val message = mapOf(
                    "type" to "FRIEND_REQUEST",
                    "senderId" to request.sender.id,
                    "senderName" to request.sender.username,
                    "message" to "${request.sender.username} 请求添加您为好友",
                    "requestId" to request.id
                )
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(message)))
            } catch (e: Exception) {
                logger.error("Failed to send friend request notification", e)
            }
        }
    }

    private fun handleGroupChatMessage(message: Message, groupId: Long) {
        val group = groupService.getGroupById(groupId)
        
        // 使用 groupService 获取群组成员
        val memberIds = getUsersInGroup(groupId)
        
        // 向所有在线成员发送消息
        for (memberId in memberIds) {
            val userSession = sessions[memberId]
            if (userSession != null && userSession.isOpen) {
                val dto = messageService.convertToMessageDTO(message)
                userSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "message",
                    "message" to dto
                ))))
            }
        }
    }

    private fun getUsersInGroup(groupId: Long): Set<Long> {
        try {
            val group = groupService.getGroupById(groupId)
            
            // 我们需要从GroupDTO中获取成员ID
            // 这里可能需要调整，取决于您的GroupDTO是否包含成员列表
            // 如果没有，您可能需要在GroupService中添加一个方法来获取群组成员
            
            // 临时解决方案：从groupService中查询
            return groupService.getGroupMembers(groupId).map { it.id }.toSet()
        } catch (e: Exception) {
            logger.error("Error getting users in group: ${e.message}")
            return emptySet()
        }
    }

    private fun handleGroupMembers(session: WebSocketSession, payload: JsonNode) {
        try {
            val groupId = payload.get("groupId").asLong()
            
            // 获取群组成员
            val members = groupService.getGroupMembers(groupId)
            
            // 转换为简单的成员列表
            val memberList = members.map { member ->
                mapOf(
                    "id" to member.id,
                    "username" to member.username,
                    "nickname" to member.nickname,
                    "avatarUrl" to member.avatarUrl
                )
            }
            
            // 发送群组成员列表
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "GROUP_MEMBERS",
                "groupId" to groupId,
                "members" to memberList
            ))))
            
        } catch (e: Exception) {
            logger.error("Error getting group members: ${e.message}", e)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "error" to "Failed to get group members: ${e.message}"
            ))))
        }
    }
}