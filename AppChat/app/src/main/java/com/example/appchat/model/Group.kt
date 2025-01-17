package com.example.appchat.model

import java.time.LocalDateTime

data class Group(
    val id: Long,
    val name: String,
    val creator: User,
    val members: List<User>,
    val avatar: String? = null,
    val announcement: String? = null,
    val createdAt: LocalDateTime
)

data class CreateGroupRequest(
    val name: String,
    val creatorId: Long,
    val memberIds: List<Long>
) 