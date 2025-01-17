package com.example.appchat.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Long,
    val username: String,
    @SerializedName("online")
    val isOnline: Boolean,
    val nickname: String? = null,
    val avatar: String? = null
) 