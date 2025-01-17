package org.example.appchathandler.dto

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatar: String?,
    val online: Boolean
) 