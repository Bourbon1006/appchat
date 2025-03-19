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
import kotlinx.coroutines.launch

class WebSocketManager {
    companion object {
        private var webSocket: WebSocket? = null
        private val messageListeners = mutableListOf<(ChatMessage) -> Unit>()
        private val userStatusListeners = CopyOnWriteArrayList<(List<UserDTO>) -> Unit>()
        private val errorListeners = mutableListOf<(String) -> Unit>()
        private val friendRequestListeners = CopyOnWriteArrayList<(FriendRequest) -> Unit>()
        private val friendRequestSentListeners = CopyOnWriteArrayList<() -> Unit>()
        private val friendRequestResultListeners = CopyOnWriteArrayList<(FriendRequest) -> Unit>()
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
            
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            
            // 确保 WebSocket URL 正确
            val wsUrl = serverUrl.replace("http://", "ws://")
            val request = Request.Builder()
                .url("$wsUrl?userId=$userId")
                .build()
            
            println("⭐ Connecting to WebSocket: ${request.url}")
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    // 调用 handleMessage 函数处理消息
                    handleMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebSocketManager", "WebSocket failure: ${t.message}")
                    t.printStackTrace()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocketManager", "WebSocket closed: $reason")
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
            println("⭐ Received WebSocket message: $text")
            try {
                // 尝试解析为消息数组
                if (text.startsWith("[")) {
                    val messages = gson.fromJson(text, Array<ChatMessage>::class.java)
                    coroutineScope.launch(Dispatchers.Main) {
                        messages.forEach { message ->
                            handleNewMessage(message)
                        }
                    }
                    return
                }

                // 尝试解析为 JSON 对象
                try {
                    val jsonObject = JSONObject(text)
                    val type = jsonObject.getString("type")
                    
                    when (type) {
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
                        "friendRequest" -> {
                            // 解析好友请求
                            val friendRequestJson = jsonObject.getJSONObject("friendRequest")
                            val friendRequest = gson.fromJson(
                                friendRequestJson.toString(),
                                FriendRequest::class.java
                            )
                            
                            // 通知所有监听器
                            coroutineScope.launch(Dispatchers.Main) {
                                friendRequestListeners.forEach { it(friendRequest) }
                            }
                        }
                        "friendDeleted" -> {
                            val friendId = jsonObject.getLong("friendId")
                            coroutineScope.launch(Dispatchers.Main) {
                                friendDeletedListeners.forEach { it(friendId) }
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
                    println("❌ Error parsing JSON: ${e.message}")
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error processing WebSocket message: ${e.message}")
            }
        }

        fun isConnected(): Boolean {
            return webSocket != null
        }

        fun sendFriendRequest(
            requestJson: String,
            onSuccess: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            try {
                if (webSocket == null) {
                    onError("WebSocket 未连接")
                    return
                }

                webSocket?.send(requestJson)
                onSuccess()
            } catch (e: Exception) {
                println("❌ Error sending friend request: ${e.message}")
                onError(e.message ?: "Unknown error")
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
    }
}