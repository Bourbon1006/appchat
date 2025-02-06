package org.example.appchathandler.dto

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.GroupDTO

data class WebSocketMessageDTO(
    val type: String,
    val message: Any? = null,
    val error: String? = null,
    val users: List<Any>? = null,
    val groupDTO: Any? = null
) 