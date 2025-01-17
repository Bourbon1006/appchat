package org.example.appchathandler.dto

import org.example.appchathandler.entity.User

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatar: String?,
    val online: Boolean
)

fun User.toDTO() = UserDTO(
    id = id,
    username = username,
    nickname = nickname,
    avatar = avatar,
    online = online
) 