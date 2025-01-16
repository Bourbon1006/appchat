package com.example.appchat.model

import java.time.LocalDateTime

data class ChatMessage(
    val id: Long = 0,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long? = null,
    val receiverName: String? = null,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isRead: Boolean = false
) 