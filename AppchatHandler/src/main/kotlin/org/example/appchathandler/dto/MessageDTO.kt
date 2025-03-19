package org.example.appchathandler.dto

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import java.time.LocalDateTime

data class MessageDTO(
    val id: Long,
    val content: String,
    val timestamp: LocalDateTime,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long?,
    val receiverName: String?,
    val groupId: Long?,
    val type: MessageType,
    val fileUrl: String?
)

// 如果这里也定义了 toDTO 扩展函数，需要删除它 