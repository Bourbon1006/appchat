package com.example.appchat.websocket

import android.util.Log
import com.example.appchat.api.ApiClient
import com.example.appchat.fragment.MessageDisplayFragment
import com.example.appchat.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.*
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast

class WebSocketManager {
    companion object {
        private var webSocket: WebSocket? = null
        private val messageListeners = mutableListOf<(ChatMessage) -> Unit>()
        private val rawMessageListeners = mutableListOf<(String) -> Unit>()
        private val userStatusListeners = CopyOnWriteArrayList<(List<UserDTO>) -> Unit>()
        private val errorListeners = mutableListOf<(String) -> Unit>()
        private val friendRequestListeners = CopyOnWriteArrayList<(FriendRequest) -> Unit>()
        private val friendRequestSentListeners = CopyOnWriteArrayList<() -> Unit>()
        private val friendRequestResultListeners = CopyOnWriteArrayList<(Long, Boolean) -> Unit>()
        private val groupCreatedListeners = CopyOnWriteArrayList<(Group) -> Unit>()
        private val sessionUpdateListeners = CopyOnWriteArrayList<(ChatMessage) -> Unit>()
        private val gson = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
        private var currentChatPartnerId: Long = 0
        private var currentUserId: Long = 0
        private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
        private var messageDisplayFragment: MessageDisplayFragment? = null
        private val friendDeletedListeners = CopyOnWriteArrayList<(Long) -> Unit>()
        private var isConnected = false

        data class WebSocketResponse(
            val type: String,
            val message: ChatMessage?,
            val error: String?,
            val users: List<UserDTO>?,
            val groupDTO: Group?
        )

        fun init(serverUrl: String, userId: Long) {
            currentUserId = userId
            println("🔐 Initializing WebSocket with userId: $userId")
            
            val wsUrl = if (serverUrl.endsWith("/")) {
                serverUrl.dropLast(1).replace("http", "ws") + "/ws?userId=$userId"
            } else {
                serverUrl.replace("http", "ws") + "/ws?userId=$userId"
            }
            
            println("⭐ Connecting to WebSocket: $wsUrl")
            
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
            
            webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    println("🌟 WebSocket connection opened")
                    handleWebSocketConnect()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    println("📥 Received message: $text")
                    rawMessageListeners.forEach { it(text) }
                    handleMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("❌ WebSocket failure: ${t.message}")
                    t.printStackTrace()
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        delay(5000)
                        init(serverUrl, userId)
                    }
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    println("🔒 WebSocket closed: $reason")
                }
            })
        }

        fun sendMessage(
            message: ChatMessage,
            onSuccess: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            try {
                val messageJson = JSONObject().apply {
                    put("type", "CHAT")
                    put("senderId", message.senderId)
                    put("senderName", message.senderName)
                    put("content", message.content)
                    put("messageType", message.type)
                    put("fileUrl", message.fileUrl)

                    if (message.groupId != null) {
                        put("groupId", message.groupId)
                    } else {
                        put("receiverId", message.receiverId)
                        put("receiverName", message.receiverName)
                    }
                }

                println("📤 Sending message: $messageJson")
                webSocket?.send(messageJson.toString())
                onSuccess()
            } catch (e: Exception) {
                println("❌ Error sending message: ${e.message}")
                onError(e.message ?: "Unknown error")
            }
        }

        fun addMessageListener(listener: (ChatMessage) -> Unit) {
            messageListeners.add(listener)
        }

        fun removeMessageListener(listener: (ChatMessage) -> Unit) {
            messageListeners.remove(listener)
        }

        fun removeMessageListeners() {
            messageListeners.clear()
        }

        fun disconnect() {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
        }

        private fun handleNewMessage(message: ChatMessage) {
            println("📨 Received new message: senderId=${message.senderId}, receiverId=${message.receiverId}, groupId=${message.groupId}, currentUserId=$currentUserId, currentChatPartnerId=$currentChatPartnerId")

            // 检查消息类型是否匹配当前会话
            val isCurrentSession = if (message.groupId != null) {
                // 群聊消息：只在群聊会话中显示
                message.groupId == currentChatPartnerId
            } else {
                // 私聊消息：只在私聊会话中显示
                currentChatPartnerId != 0L && message.groupId == null &&
                ((message.senderId == currentUserId && message.receiverId == currentChatPartnerId) ||
                (message.senderId == currentChatPartnerId && message.receiverId == currentUserId))
            }

            if (!isCurrentSession) {
                println("⚠️ Message does not belong to current session, skipping...")
                return
            }

            // 检查是否需要标记为已读
            val shouldMarkAsRead = if (message.groupId != null) {
                message.groupId == currentChatPartnerId
            } else {
                message.receiverId == currentUserId && message.senderId == currentChatPartnerId
            }

            if (shouldMarkAsRead) {
                println("✅ Message qualifies for read status update")
                coroutineScope.launch {
                    try {
                        println("📝 Calling markSessionAsRead API...")
                        val response = ApiClient.apiService.markSessionAsRead(
                            userId = currentUserId,
                            partnerId = if (message.groupId != null) message.groupId else currentChatPartnerId,
                            type = if (message.groupId != null) "GROUP" else "PRIVATE"
                        )
                        println("✅ markSessionAsRead API response: $response")
                        
                        // API 调用成功后立即获取并更新会话列表
                        val sessions = ApiClient.apiService.getMessageSessions(currentUserId)
                        messageDisplayFragment?.updateSessions(sessions)
                        
                    } catch (e: Exception) {
                        println("❌ Error marking message as read: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } else {
                println("❌ Message does not qualify for read status update: groupId=${message.groupId}, receiverId=${message.receiverId}, senderId=${message.senderId}, currentChatPartnerId=$currentChatPartnerId")
            }

            // 通知所有监听器
            println("📢 Notifying ${messageListeners.size} listeners")
            messageListeners.forEach { it(message) }
        }

        fun setCurrentChat(userId: Long, partnerId: Long, isGroup: Boolean = false) {
            println("🔄 Setting current chat: userId=$userId, partnerId=$partnerId, isGroup=$isGroup (previous partnerId=$currentChatPartnerId)")
            currentChatPartnerId = partnerId

            // 只有当 partnerId 不为 0 时才标记为已读
            if (partnerId != 0L) {
                coroutineScope.launch {
                    try {
                        println("📝 Marking session as read when entering chat...")
                        val response = ApiClient.apiService.markSessionAsRead(
                            userId = userId,
                            partnerId = partnerId,
                            type = if (isGroup) "GROUP" else "PRIVATE"
                        )
                        println("✅ markSessionAsRead API response when entering chat: $response")
                        
                        // API 调用成功后立即获取并更新会话列表
                        val sessions = ApiClient.apiService.getMessageSessions(userId)
                        messageDisplayFragment?.updateSessions(sessions)
                        
                    } catch (e: Exception) {
                        println("❌ Error marking session as read when entering chat: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } else {
                println("⚠️ Skipping markSessionAsRead because partnerId is 0")
            }
        }

        fun setMessageDisplayFragment(fragment: MessageDisplayFragment?) {
            messageDisplayFragment = fragment
        }

        private fun handleMessage(text: String) {
            try {
                val jsonObject = JSONObject(text)
                val type = jsonObject.getString("type")
                
                when (type) {
                    "CHAT" -> {
                        val message = gson.fromJson(text, ChatMessage::class.java)
                        messageListeners.forEach { it(message) }
                    }
                    "FRIEND_REQUEST" -> {
                        try {
                            // 直接从 JSONObject 解析数据
                            val senderId = jsonObject.getLong("senderId")
                            val senderName = jsonObject.getString("senderName")
                            val senderAvatar = jsonObject.optString("senderAvatar")
                            val requestId = jsonObject.getLong("requestId")
                            
                            // 创建 FriendRequest 对象，确保所有必要字段都有值
                            val request = FriendRequest(
                                id = requestId,
                                sender = UserDTO(
                                    id = senderId,
                                    username = senderName,
                                    nickname = null,
                                    avatarUrl = senderAvatar,
                                    isOnline = true
                                ),
                                receiver = UserDTO(
                                    id = currentUserId,
                                    username = "",  // 这里可以留空，因为是当前用户
                                    nickname = null,
                                    avatarUrl = null,
                                    isOnline = true
                                ),
                                status = "PENDING",
                                timestamp = LocalDateTime.now().toString()
                            )

                            coroutineScope.launch(Dispatchers.Main) {
                                friendRequestListeners.forEach { it(request) }
                            }
                        } catch (e: Exception) {
                            println("❌ Error handling friend request: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    "sessions_update", "message_read" -> {
                        coroutineScope.launch(Dispatchers.Main) {
                            // 获取最新的会话列表
                            val sessions = jsonObject.optJSONArray("sessions")?.let {
                                gson.fromJson(it.toString(), Array<MessageSession>::class.java).toList()
                            } ?: emptyList()
                            
                            // 直接更新会话列表
                            messageDisplayFragment?.updateSessions(sessions)
                        }
                    }
                    "friendDeleted" -> {
                        val friendId = jsonObject.getLong("friendId")
                        coroutineScope.launch(Dispatchers.Main) {
                            friendDeletedListeners.forEach { it(friendId) }
                        }
                    }
                    "FRIEND_REQUEST_RESULT" -> {
                        val requestId = jsonObject.getLong("requestId")
                        val status = jsonObject.getString("status")
                        val accepted = jsonObject.getBoolean("accepted")
                        
                        // 使用 Main 线程更新 UI
                        coroutineScope.launch(Dispatchers.Main) {
                            // 只通知监听器
                            friendRequestResultListeners.forEach { it(requestId, accepted) }
                        }
                    }
                    // 尝试解析为 WebSocketResponse
                    else -> {
                        val response = gson.fromJson(text, WebSocketResponse::class.java)
                        coroutineScope.launch(Dispatchers.Main) {
                            when (response.type) {
                                "message" -> {
                                    response.message?.let { message ->
                                        // 只在这里处理一次消息
                                        handleNewMessage(message)
                                        
                                        // 只更新 UI，不处理已读状态
                                        sessionUpdateListeners.forEach { 
                                            it(message)
                                        }
                                    }
                                }
                                "error" -> {
                                    response.error?.let { error ->
                                        errorListeners.forEach { listener ->
                                            listener(error)
                                        }
                                    }
                                }
                                "users" -> {
                                    response.users?.let { users ->
                                        userStatusListeners.forEach { listener ->
                                            listener(users)
                                        }
                                    }
                                }
                                "group" -> {
                                    response.groupDTO?.let { group ->
                                        groupCreatedListeners.forEach { listener ->
                                            listener(group)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error handling message: ${e.message}")
                e.printStackTrace()
            }
        }

        fun isConnected(): Boolean {
            return webSocket != null
        }

        fun sendFriendRequest(requestJson: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
            if (!isConnected()) {
                onError("WebSocket 未连接")
                return
            }

            try {
                println("📤 Sending friend request: $requestJson")
                webSocket?.send(requestJson)?.let { success ->
                    if (success) {
                        println("✅ Friend request sent successfully")
                        onSuccess()
                    } else {
                        println("❌ Failed to send friend request")
                        onError("发送失败")
                    }
                } ?: run {
                    println("❌ WebSocket is null")
                    onError("WebSocket 未连接")
                }
            } catch (e: Exception) {
                println("❌ Error sending friend request: ${e.message}")
                e.printStackTrace()
                onError("发送失败: ${e.message}")
            }
        }

        fun addFriendRequestListener(listener: (FriendRequest) -> Unit) {
            friendRequestListeners.add(listener)
        }

        fun removeFriendRequestListener(listener: (FriendRequest) -> Unit) {
            friendRequestListeners.remove(listener)
        }

        fun removeFriendRequestListeners() {
            friendRequestListeners.clear()
        }

        fun handleFriendRequest(requestId: Long, accept: Boolean) {
            try {
                val requestJson = JSONObject().apply {
                    put("type", "HANDLE_FRIEND_REQUEST")
                    put("requestId", requestId)
                    put("accept", accept)
                }
                
                webSocket?.send(requestJson.toString())
                println("📤 Sent friend request response: requestId=$requestId, accept=$accept")
            } catch (e: Exception) {
                println("❌ Error handling friend request: ${e.message}")
                e.printStackTrace()
            }
        }

        // 添加会话更新监听器
        fun addSessionUpdateListener(listener: (ChatMessage) -> Unit) {
            sessionUpdateListeners.add(listener)
        }

        // 移除会话更新监听器
        fun removeSessionUpdateListener(listener: (ChatMessage) -> Unit) {
            sessionUpdateListeners.remove(listener)
        }

        fun addFriendDeletedListener(listener: (Long) -> Unit) {
            friendDeletedListeners.add(listener)
        }

        fun removeFriendDeletedListener(listener: (Long) -> Unit) {
            friendDeletedListeners.remove(listener)
        }

        fun addFriendRequestResultListener(listener: (Long, Boolean) -> Unit) {
            friendRequestResultListeners.add(listener)
        }

        fun removeFriendRequestResultListener(listener: (Long, Boolean) -> Unit) {
            friendRequestResultListeners.remove(listener)
        }

        private fun handleWebSocketConnect() {
            isConnected = true
            println("✅ WebSocket connected")
            
            // 连接成功后立即发送请求获取待处理的好友请求数量
            coroutineScope.launch {
                try {
                    val response = ApiClient.apiService.getPendingRequests(currentUserId)
                    if (response.isSuccessful) {
                        val requests = response.body() ?: emptyList()
                        // 通知所有监听器更新未处理请求数量
                        coroutineScope.launch(Dispatchers.Main) {
                            pendingRequestCountListeners.forEach { it(requests.size) }
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Error fetching pending requests: ${e.message}")
                }
            }
        }

        // 添加待处理请求数量监听器列表
        private val pendingRequestCountListeners = CopyOnWriteArrayList<(Int) -> Unit>()

        fun addPendingRequestCountListener(listener: (Int) -> Unit) {
            pendingRequestCountListeners.add(listener)
        }

        fun removePendingRequestCountListener(listener: (Int) -> Unit) {
            pendingRequestCountListeners.remove(listener)
        }
    }
}