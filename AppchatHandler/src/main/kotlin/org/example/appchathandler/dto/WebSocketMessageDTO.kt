package org.example.appchathandler.dto

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.GroupDTO

data class WebSocketMessageDTO(
    val type: String,
    val message: MessageDTO? = null,
    val messages: List<MessageDTO>? = null,
    val users: List<UserDTO>? = null,
    val user: UserDTO? = null,
    val groupDTO: GroupDTO? = null,
    val error: String? = null
) 