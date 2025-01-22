package com.example.appchat.model

import java.time.LocalDateTime

data class ChatMessage(
    val id: Long? = null,
    val type: MessageType = MessageType.TEXT,
    val content: String,
    val senderId: Long,
    val senderName: String? = null,
    val receiverId: Long? = null,
    val groupId: Long? = null,
    val timestamp: LocalDateTime? = null,
    val fileUrl: String? = null
)
