package org.example.appchathandler.dto

import java.time.LocalDateTime

data class MessageSessionDTO(
    val id: Long,
    val partnerId: Long,
    val partnerName: String,
    val partnerAvatar: String?,
    val lastMessage: String,
    val lastMessageTime: LocalDateTime,
    val unreadCount: Int,
    val type: String
) 