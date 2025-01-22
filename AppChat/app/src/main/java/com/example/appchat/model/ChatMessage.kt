package com.example.appchat.model

import java.time.LocalDateTime

data class ChatMessage(
    val id: Long? = null,
    val content: String,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long? = null,
    val receiverName: String? = null,
    val groupId: Long? = null,
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val timestamp: LocalDateTime? = null
)
