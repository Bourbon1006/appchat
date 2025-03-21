package org.example.appchathandler.dto

import org.example.appchathandler.entity.User

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val onlineStatus: Int = 0
)

fun User.toDTO() = UserDTO(
    id = id,
    username = username,
    nickname = nickname,
    avatarUrl = avatarUrl,
    onlineStatus = onlineStatus
) 