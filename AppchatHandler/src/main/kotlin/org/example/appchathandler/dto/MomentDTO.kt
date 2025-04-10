package org.example.appchathandler.dto

import LikeUserDTO
import java.time.LocalDateTime

data class MomentDTO(
    val id: Long,
    val userId: Long,
    val username: String,
    val userNickname: String? = null,
    val userAvatar: String?,
    val content: String,
    val imageUrl: String?,
    val createTime: LocalDateTime,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,
    val comments: List<MomentCommentDTO>,
    val likeUsers: List<UserDTO>? = null
)

data class MomentCommentDTO(
    val id: Long,
    val userId: Long,
    val username: String,
    val userNickname: String? = null,
    val userAvatar: String?,
    val content: String,
    val createTime: LocalDateTime
) 