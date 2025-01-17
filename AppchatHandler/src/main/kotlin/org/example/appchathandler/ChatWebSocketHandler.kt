package org.example.appchathandler

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.appchathandler.dto.UserDTO
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
import org.example.appchathandler.dto.ChatMessageDTO

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
        val avatar: String?,
        val online: Boolean
    )

    data class FriendRequestDTO(
        val id: Long,
        val sender: UserDTO,
        val receiver: UserDTO,
        val status: String,
        val timestamp: LocalDateTime
    )

    private fun User.toDTO() = UserDTO(
        id = id,
        username = username,
        nickname = nickname,
        avatar = avatar,
        online = online
    )

    private fun Message.toDTO() = ChatMessageDTO(
        id = id,
        content = content,
        timestamp = timestamp,
        senderId = sender.id,
        senderName = sender.username,
        receiverId = receiver?.id,
        receiverName = receiver?.username,
        groupId = group?.id,
        type = type.name,
        fileUrl = fileUrl,
        isRead = isRead
    )

    private fun FriendRequest.toDTO() = FriendRequestDTO(
        id = id,
        sender = sender.toDTO(),
        receiver = receiver.toDTO(),
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

                // 发送待处理的好友请求
                val pendingRequests = friendRequestService.getPendingRequests(userId)
                if (pendingRequests.isNotEmpty()) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "pendingFriendRequests",
                        "requests" to pendingRequests.map { it.toDTO() }
                    ))))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                session.close()
            }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            println("Received message: ${message.payload}")
            val messageNode = objectMapper.readTree(message.payload)
            val messageType = messageNode.get("type")?.asText() ?: "CHAT"
            
            when (messageType) {
                "CHAT" -> {
                    val senderId = messageNode.get("senderId").asLong()
                    val content = messageNode.get("content").asText()
                    val groupId = messageNode.get("groupId")?.let { if (it.isNull) null else it.asLong() }
                    val receiverId = messageNode.get("receiverId")?.let { if (it.isNull) null else it.asLong() }
                    val type = MessageType.valueOf(messageNode.get("messageType")?.asText() ?: "TEXT")
                    val fileUrl = messageNode.get("fileUrl")?.let { if (it.isNull) null else it.asText() }
                    
                    val savedMessage = messageService.createMessage(
                        content = content,
                        senderId = senderId,
                        receiverId = receiverId,
                        groupId = groupId,
                        type = type,
                        fileUrl = fileUrl
                    )

                    if (groupId != null) {
                        // 群聊消息，发送给所有群成员
                        val group = groupService.getGroup(groupId)
                        group.members.forEach { member ->
                            sessions[member.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                                "type" to "message",
                                "message" to savedMessage.toDTO()
                            ))))
                        }
                    } else {
                        // 私聊消息
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
                }
                "FILE" -> handleFileTransfer(messageNode)
                "FRIEND_REQUEST" -> handleFriendRequest(messageNode, session)
                "HANDLE_FRIEND_REQUEST" -> {
                    val requestId = messageNode.get("requestId").asLong()
                    val accept = messageNode.get("accept").asBoolean()
                    
                    val request = friendRequestService.handleFriendRequest(requestId, accept)
                    
                    // 通知发送者请求结果
                    sessions[request.sender.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "friendRequestResult",
                        "friendRequest" to request
                    ))))
                }
                "CREATE_GROUP" -> handleCreateGroup(messageNode, session)
                else -> {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "error",
                        "message" to "Unsupported message type: $messageType"
                    ))))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error message payload: ${message.payload}")
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

    private fun handleFriendRequest(messageNode: JsonNode, session: WebSocketSession) {
        try {
            println("Processing friend request")
            val senderId = messageNode["senderId"].asLong()
            val receiverId = messageNode["receiverId"].asLong()
            
            println("Sending friend request from $senderId to $receiverId")
            val request = friendRequestService.sendFriendRequest(senderId, receiverId)
            println("Friend request created: $request")
            
            // 通知接收者
            sessions[receiverId]?.let { receiverSession ->
                println("Sending notification to receiver")
                receiverSession.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "friendRequest",
                    "friendRequest" to request.toDTO()
                ))))
            } ?: println("Receiver not online")

            // 通知发送者请求已发送
            sessions[senderId]?.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "friendRequestSent",
                "friendRequest" to request.toDTO()
            ))))
        } catch (e: Exception) {
            println("Error handling friend request: ${e.message}")
            e.printStackTrace()
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "error",
                "message" to e.message
            ))))
        }
    }

    private fun handleCreateGroup(messageNode: JsonNode, session: WebSocketSession) {
        val name = messageNode["name"].asText()
        val creatorId = messageNode["creatorId"].asLong()
        val memberIds = messageNode["memberIds"].map { it.asLong() }

        try {
            val group = groupService.createGroup(name, creatorId, memberIds)
            
            // 通知所有群成员
            group.members.forEach { member ->
                sessions[member.id]?.sendMessage(TextMessage(objectMapper.writeValueAsString(
                    WebSocketMessageDTO(
                        type = "groupCreated",
                        group = group
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

    private fun handleGroupMessage(message: Map<String, Any>, senderId: Long) {
        val groupId = (message["groupId"] as Number).toLong()
        val group = groupService.getGroup(groupId)
        
        // 检查发送者是否是群成员
        if (group.members.none { it.id == senderId }) {
            return
        }

        val content = message["content"] as String
        val type = MessageType.valueOf(message["messageType"] as String)
        val fileUrl = message["fileUrl"] as? String

        val savedMessage = messageService.createMessage(
            content = content,
            senderId = senderId,
            groupId = groupId,
            type = type,
            fileUrl = fileUrl
        )

        // 发送消息给所有群成员
        group.members.forEach { member ->
            sessions[member.id]?.let { session ->
                val response = WebSocketMessageDTO(
                    type = "message",
                    message = savedMessage.toDTO()
                )
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
            }
        }
    }
} 