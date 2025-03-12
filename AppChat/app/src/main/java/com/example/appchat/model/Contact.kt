package com.example.appchat.model

data class Contact(
    val id: Long,
    val name: String,
    val avatarUrl: String,
    val isOnline: Boolean = false
) 