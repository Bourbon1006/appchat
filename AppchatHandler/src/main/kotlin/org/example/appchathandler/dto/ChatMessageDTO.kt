package org.example.appchathandler.dto

import org.example.appchathandler.entity.Message
import java.time.LocalDateTime

data class ChatMessageDTO(
    val id: Long,
    val content: String,
    val timestamp: LocalDateTime,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long?,
    val receiverName: String?,
    val groupId: Long?,
    val type: String,
    val fileUrl: String?,
    val isRead: Boolean
)

fun Message.toDTO() = ChatMessageDTO(
    id = id,
    content = content,
    timestamp = timestamp,
    senderId = sender.id,
    senderName = sender.username,
    receiverId = receiver?.id,
    receiverName = receiver?.username,
    groupId = group?.id,
    type = type.name,
    fileUrl = fileUrl,
    isRead = isRead
) 