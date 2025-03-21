package com.example.appchat.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Long,
    val username: String,
    @SerializedName("online")
    val isOnline: Boolean = false,
    val nickname: String? = null,
    val avatar: String? = null,
    val avatarUrl: String? = null,
    @SerializedName("onlineStatus")
    val onlineStatus: Int = 0  // 添加 onlineStatus 属性，默认为 0 (离线)
) 