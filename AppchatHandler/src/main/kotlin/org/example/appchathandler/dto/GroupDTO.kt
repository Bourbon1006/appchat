package org.example.appchathandler.dto

import org.example.appchathandler.entity.Group

data class GroupDTO(
    val id: Long,
    val name: String,
    val creator: UserDTO,
    val members: List<UserDTO>
)

fun Group.toDTO() = GroupDTO(
    id = id,
    name = name,
    creator = creator.toDTO(),
    members = members.map { it.toDTO() }
) 