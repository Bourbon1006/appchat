package com.example.appchat.model

import java.time.LocalDateTime

data class MessageSession(
    val id: Long,
    val partnerId: Long,  // 聊天对象ID（用户ID或群组ID）
    val partnerName: String,  // 聊天对象名称
    val partnerAvatar: String?,  // 聊天对象头像
    val lastMessage: String,  // 最后一条消息内容
    val lastMessageTime: LocalDateTime,  // 最后一条消息时间
    val type: String,  // 会话类型：private 或 group
    val unreadCount: Int = 0
) 