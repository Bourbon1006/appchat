package com.example.appchat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class Contact(
    val id: Long,
    val username: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val onlineStatus: Int = 0  // 0: 离线, 1: 在线, 2: 忙碌
) : Parcelable, Serializable