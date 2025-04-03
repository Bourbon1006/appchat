package com.example.appchat.model

import android.content.Context
import android.os.Parcelable
import com.example.appchat.R
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class Contact(
    val id: Long,
    val username: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    var onlineStatus: Int = 0  // 0: 离线, 1: 在线, 2: 忙碌
) : Parcelable, Serializable {
    fun getFullAvatarUrl(context: Context): String {
        val baseUrl = context.getString(
            R.string.server_url_format,
            context.getString(R.string.server_ip),
            context.getString(R.string.server_port)
        )
        return if (avatarUrl?.startsWith("/") == true) {
            baseUrl.removeSuffix("/") + avatarUrl
        } else {
            avatarUrl ?: "$baseUrl/api/users/$id/avatar"
        }
    }
}