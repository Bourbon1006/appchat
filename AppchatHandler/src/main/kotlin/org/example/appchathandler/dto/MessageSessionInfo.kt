package org.example.appchathandler.dto

import java.time.LocalDateTime

interface MessageSessionInfo {
    val id: Long
    val partnerId: Long
    val partnerName: String
    val partnerAvatar: String?
    val lastMessage: String
    val lastMessageTime: LocalDateTime
    enum class Type {
        GROUP, PRIVATE
    }
    val unreadCount: Int
} 