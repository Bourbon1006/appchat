package com.example.appchat.websocket

import android.util.Log
import com.example.appchat.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.*
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object WebSocketManager {
    private var webSocket: WebSocket? = null
    private val messageListeners = CopyOnWriteArrayList<(ChatMessage) -> Unit>()
    private val userStatusListeners = CopyOnWriteArrayList<(List<UserDTO>) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val friendRequestListeners = CopyOnWriteArrayList<(FriendRequest) -> Unit>()
    private val friendRequestSentListeners = CopyOnWriteArrayList<() -> Unit>()
    private val friendRequestResultListeners = CopyOnWriteArrayList<(FriendRequest) -> Unit>()
    private val groupCreatedListeners = CopyOnWriteArrayList<(Group) -> Unit>()
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .create()

    fun init(serverUrl: String, userId: Long) {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url("$serverUrl?userId=$userId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    println("⭐ Received WebSocket message: $text")
                    val wsMessage = gson.fromJson(text, WebSocketMessage::class.java)
                    println("📝 Parsed message type: ${wsMessage.type}")
                    
                    when (wsMessage.type) {
                        "history" -> {
                            println("📜 Processing history messages")
                            wsMessage.messages?.forEach { message ->
                                messageListeners.forEach { listener ->
                                    listener(message)
                                }
                            }
                        }
                        "message", "CHAT" -> {
                            wsMessage.message?.let { message ->
                                println("💬 Processing new message: $message")
                                messageListeners.forEach { listener ->
                                    listener(message)
                                }
                            }
                        }
                        "users" -> {
                            wsMessage.users?.let { users ->
                                println("👥 Processing users message: ${users.map { "${it.username}(${it.isOnline})" }}")
                                userStatusListeners.forEach { listener ->
                                    listener(users)
                                }
                            }
                        }
                        "userStatus" -> {
                            wsMessage.user?.let { user ->
                                println("👤 Processing user status message: ${user.username}(online=${user.isOnline})")
                                userStatusListeners.forEach { listener ->
                                    listener(listOf(user))
                                }
                            }
                        }
                        "error" -> {
                            wsMessage.error?.let { error ->
                                println("❌ Received error message: $error")
                                errorListeners.forEach { listener ->
                                    listener(error)
                                }
                            }
                        }
                        "friendRequest" -> {
                            wsMessage.friendRequest?.let { request ->
                                println("🤝 Received new friend request from ${request.sender.username}")
                                friendRequestListeners.forEach { listener ->
                                    listener(request)
                                }
                            }
                        }
                        "friendRequestSent" -> {
                            println("✈️ Friend request sent successfully")
                            friendRequestSentListeners.forEach { listener ->
                                listener()
                            }
                        }
                        "friendRequestResult" -> {
                            wsMessage.friendRequest?.let { request ->
                                println("📫 Received friend request result: ${request.status} from ${request.receiver.username}")
                                friendRequestResultListeners.forEach { listener ->
                                    listener(request)
                                }
                            }
                        }
                        "groupCreated" -> {
                            wsMessage.group?.let { group ->
                                println("👥 New group created: ${group.name}")
                                groupCreatedListeners.forEach { listener ->
                                    listener(group)
                                }
                            }
                        }
                        "groupMessage" -> {
                            wsMessage.message?.let { message ->
                                println("👥 Received group message for group ${message.groupId}")
                                messageListeners.forEach { listener ->
                                    listener(message)
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

    fun sendMessage(message: ChatMessage) {
        if (webSocket == null) {
            Log.e("WebSocketManager", "WebSocket未连接")
            return
        }

        val messageMap = mutableMapOf(
            "type" to "CHAT",
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "content" to message.content,
            "messageType" to message.type.name,
            "receiverId" to (message.receiverId ?: throw IllegalArgumentException("接收者ID不能为空")),
            "receiverName" to (message.receiverName ?: "")
        )

        val json = gson.toJson(messageMap)
        Log.d("WebSocketManager", "发送消息: $json")
        webSocket?.send(json)
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
}