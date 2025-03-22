package org.example.appchathandler.dto

import org.example.appchathandler.entity.Group
import java.time.LocalDateTime

data class GroupDTO(
    val id: Long,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val creator: UserDTO,
    val owner: UserDTO,
    val createdAt: LocalDateTime,
    val memberCount: Int
)

data class GroupCreateRequest(
    val name: String,
    val description: String? = null,
    val creatorId: Long,
    val memberIds: List<Long>? = null
)

fun Group.toDTO(): GroupDTO {
    return GroupDTO(
        id = this.id,
        name = this.name,
        description = this.description,
        avatarUrl = this.avatarUrl,
        creator = this.creator.toDTO(),
        owner = this.owner.toDTO(),
        createdAt = this.createdAt,
        memberCount = this.members.size
    )
} 