package org.example.appchathandler.dto

import java.time.LocalDateTime

data class MessageSessionDTO(
    override val id: Long,
    override val partnerId: Long,
    override val partnerName: String,
    override val partnerAvatar: String?,
    override val lastMessage: String,
    override val lastMessageTime: LocalDateTime,
    override val unreadCount: Int,
    val type: MessageSessionInfo.Type  // 改用枚举类型
) : MessageSessionInfo