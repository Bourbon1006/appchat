package com.example.appchat.model

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
)

data class AuthResponse(
    val token: String,
    val userId: Long,
    val username: String,
    val name: String,
    val message: String? = null
) 