package com.example.appchat.model

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val onlineStatus: Int? = null,
    val isAdmin: Boolean? = false,  // 添加这个属性
    val requestId: Long? = null  // 添加这个字段
) 