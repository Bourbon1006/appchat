package com.example.appchat.model

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val isOnline: Boolean = false,
    val onlineStatus: Int = 0  // 添加 onlineStatus 属性，默认为 0 (离线)
) 