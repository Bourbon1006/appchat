package com.example.appchat.model

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val isOnline: Boolean = false
) 