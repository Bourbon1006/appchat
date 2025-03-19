package com.example.appchat.model

import java.time.LocalDateTime

data class MessageSession(
    val id: Long,
    val partnerId: Long,  // 聊天对象ID（用户ID或群组ID）
    val partnerName: String,  // 聊天对象名称
    val lastMessage: String,  // 最后一条消息内容
    val lastMessageTime: LocalDateTime,  // 最后一条消息时间
    val unreadCount: Int,  // 保持为 val，通过创建新对象来更新
    val type: String,  // 会话类型：private 或 group
    val partnerAvatar: String?  // 聊天对象头像
) {
    fun markAsRead() = copy(unreadCount = 0)  // 提供一个创建已读版本的方法
} 