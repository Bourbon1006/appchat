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
    val senderNickname: String? = null,
    val receiverId: Long? = null,
    val receiverName: String? = null,
    val groupId: Long? = null,
    val groupName: String? = null,
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null
)

// 如果这里也定义了 toDTO 扩展函数，需要删除它 