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
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String?
)

fun Message.toDTO() = MessageDTO(
    id = id,
    content = content,
    timestamp = timestamp,
    senderId = sender.id,
    senderName = sender.username,
    receiverId = receiver?.id,
    receiverName = receiver?.username,
    groupId = group?.id,
    type = type ?: MessageType.TEXT,
    fileUrl = fileUrl
) 