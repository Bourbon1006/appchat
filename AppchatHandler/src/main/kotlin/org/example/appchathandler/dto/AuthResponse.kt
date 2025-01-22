package org.example.appchathandler.dto

data class AuthResponse(
    val token: String,
    val userId: Long,
    val username: String
) 