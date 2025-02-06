package com.example.appchat.model

data class WebSocketMessage(
    val type: String,
    val message: ChatMessage? = null,
    val messages: List<ChatMessage>? = null,
    val users: List<UserDTO>? = null,
    val user: UserDTO? = null,
    val error: String? = null,
    val friendRequest: FriendRequest? = null,
    val group: Group? = null
) 