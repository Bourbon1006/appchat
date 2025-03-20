package com.example.appchat.model

import com.example.appchat.model.User
import java.time.LocalDateTime

data class Moment(
    val id: Long,
    val userId: Long,
    val username: String,
    val userAvatar: String?,
    val content: String,
    val imageUrl: String?,
    val createTime: LocalDateTime,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,
    val comments: List<MomentComment>,
    val likeUsers: List<User>? = null
)

data class MomentComment(
    val id: Long,
    val userId: Long,
    val username: String,
    val userAvatar: String?,
    val content: String,
    val createTime: LocalDateTime
) 