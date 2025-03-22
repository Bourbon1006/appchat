package org.example.appchathandler.dto

import java.time.LocalDateTime

data class GroupMemberDTO(
    val userId: Long,
    val groupId: Long,
    val isAdmin: Boolean,
    val joinedAt: LocalDateTime
) 