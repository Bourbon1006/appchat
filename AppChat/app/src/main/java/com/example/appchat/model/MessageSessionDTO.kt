package com.example.appchat.model

import java.time.LocalDateTime

data class MessageSessionDTO(
    val id: Long,
    val partnerId: Long,
    val partnerName: String,
    val lastMessage: String,
    val lastMessageTime: LocalDateTime,
    val unreadCount: Int,
    val type: String,
    val partnerAvatar: String?
) {
    fun toMessageSession(): MessageSession {
        return MessageSession(
            id = id,
            partnerId = partnerId,
            partnerName = partnerName,
            partnerAvatar = partnerAvatar,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime.toString(),
            unreadCount = unreadCount,
            type = type
        )
    }
} 