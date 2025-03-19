package org.example.appchathandler.dto

import org.example.appchathandler.entity.User
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.websocket.ChatWebSocketHandler.UserStatusDTO
import org.example.appchathandler.websocket.ChatWebSocketHandler.FriendRequestDTO
import java.time.LocalDateTime

fun User.toStatusDTO() = UserStatusDTO(
    id = id,
    username = username,
    nickname = nickname,
    avatarUrl = avatarUrl,
    isOnline = isOnline
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
    type = type,
    fileUrl = fileUrl
)

fun FriendRequest.toDTO() = FriendRequestDTO(
    id = id,
    sender = sender.toStatusDTO(),
    receiver = receiver.toStatusDTO(),
    status = status.name,
    timestamp = timestamp
) 