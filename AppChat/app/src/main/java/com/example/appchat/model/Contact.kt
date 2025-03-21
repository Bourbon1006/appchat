package com.example.appchat.model

data class Contact(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val onlineStatus: Int = 0  // 0: 离线, 1: 在线, 2: 忙碌
) 