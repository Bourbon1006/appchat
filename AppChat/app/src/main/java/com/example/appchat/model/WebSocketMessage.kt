package com.example.appchat.model

data class WebSocketMessage(
    val type: String,
    val message: ChatMessage? = null,
    val messages: List<ChatMessage>? = null,
    val users: List<UserDTO>? = null,
    val user: UserDTO? = null,
    val error: String? = null,
    val requests: List<FriendRequest>? = null, // 确保这里定义了 requests
    val friendRequest: FriendRequest? = null,
    val group: Group? = null
) 