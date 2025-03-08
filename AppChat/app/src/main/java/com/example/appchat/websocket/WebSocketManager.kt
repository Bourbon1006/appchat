package com.example.appchat.websocket

import android.util.Log
import com.example.appchat.api.ApiClient
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
        private val gson = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
        private var currentChatPartnerId: Long = 0
        private var currentUserId: Long = 0
        private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

        data class WebSocketResponse(
            val type: String,
            val message: ChatMessage?,
            val error: String?,
            val users: List<UserDTO>?,
            val groupDTO: Group?
        )

        fun init(serverUrl: String, userId: Long) {
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
                    println("⭐ Received WebSocket message: $text")
                    try {
                        // 尝试解析为消息数组
                        if (text.startsWith("[")) {
                            val messages = gson.fromJson(text, Array<ChatMessage>::class.java)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                messages.forEach { message ->
                                    handleNewMessage(message)
                                }
                            }
                            return
                        }

                        // 解析为 WebSocketResponse
                        val response = gson.fromJson(text, WebSocketResponse::class.java)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            when (response.type) {
                                "message" -> {
                                    response.message?.let { message ->
                                        handleNewMessage(message)
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("❌ Error processing WebSocket message: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebSocketManager", "WebSocket failure: ${t.message}")
                    errorListeners.forEach { listener ->
                        listener("WebSocket错误: ${t.message}")
                    }
                    // 自动重连
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("WebSocketManager", "正在尝试重新连接...")
                        init(serverUrl, userId)
                    }, 5000) // 5秒后重试
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
            if (message.senderId != currentChatPartnerId) {
                messageListeners.forEach { it(message) }
                return
            }

            coroutineScope.launch {
                try {
                    ApiClient.apiService.markSessionAsRead(
                        userId = currentUserId,
                        partnerId = message.senderId,
                        type = if (message.groupId != null) "group" else "private"
                    )
                } catch (e: Exception) {
                    // 忽略错误
                }
            }

            messageListeners.forEach { it(message) }
        }

        fun setCurrentChat(userId: Long, partnerId: Long) {
            currentUserId = userId
            currentChatPartnerId = partnerId
        }
    }
}