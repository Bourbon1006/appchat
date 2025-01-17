package com.example.appchat.model

data class WebSocketMessage(
    val type: String,
    val messages: List<ChatMessage>? = null,
    val message: ChatMessage? = null,
    val users: List<User>? = null,
    val user: User? = null,
    val friendRequest: FriendRequest? = null,
    val error: String? = null
) 