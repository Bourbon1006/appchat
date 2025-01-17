package org.example.appchathandler.dto

import org.example.appchathandler.controller.FriendRequestController

data class WebSocketMessageDTO(
    val type: String,
    val message: ChatMessageDTO? = null,
    val users: List<UserDTO>? = null,
    val user: UserDTO? = null,
    val error: String? = null,
    val friendRequest: FriendRequestController.FriendRequestDTO? = null,
    val group: GroupDTO? = null
) 