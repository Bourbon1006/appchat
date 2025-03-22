package com.example.appchat.model

import java.time.LocalDateTime

data class Group(
    val id: Long,
    val name: String,
    val announcement: String?,
    val creator: UserDTO,
    val members: List<UserDTO>,
    val avatarUrl: String? = null,
    val createdAt: LocalDateTime,
    val memberCount: Int = 0
)

data class CreateGroupRequest(
    val name: String,
    val creatorId: Long,
    val memberIds: List<Long>
) 