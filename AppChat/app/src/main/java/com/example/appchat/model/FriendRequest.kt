package com.example.appchat.model

import java.time.LocalDateTime

data class FriendRequest(
    val id: Long,
    val sender: User,
    val receiver: User,
    val status: String,
    val timestamp: LocalDateTime
) 