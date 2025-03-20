package com.example.appchat.model

import java.time.LocalDateTime

data class FriendRequest(
    val id: Long,
    val sender: UserDTO,
    val receiver: UserDTO,
    val status: String,
    val timestamp: String
) 