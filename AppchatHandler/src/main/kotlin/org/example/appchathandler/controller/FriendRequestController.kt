package org.example.appchathandler.controller

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.service.FriendRequestService
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/friend-requests")
class FriendRequestController(private val friendRequestService: FriendRequestService) {
    
    data class FriendRequestDTO(
        val id: Long,
        val sender: UserDTO,
        val receiver: UserDTO,
        val status: String,
        val timestamp: LocalDateTime
    )

    private fun User.toDTO() = UserDTO(
        id = id,
        username = username,
        nickname = nickname,
        avatar = avatar,
        online = online
    )

    private fun FriendRequest.toDTO() = FriendRequestDTO(
        id = id,
        sender = sender.toDTO(),
        receiver = receiver.toDTO(),
        status = status.name,
        timestamp = timestamp
    )

    @PostMapping
    fun sendFriendRequest(@RequestBody request: Map<String, Long>): FriendRequestDTO {
        val senderId = request["senderId"] ?: throw IllegalArgumentException("Missing senderId")
        val receiverId = request["receiverId"] ?: throw IllegalArgumentException("Missing receiverId")
        return friendRequestService.sendFriendRequest(senderId, receiverId).toDTO()
    }

    @PutMapping("/{requestId}")
    fun handleFriendRequest(
        @PathVariable requestId: Long,
        @RequestBody request: Map<String, Boolean>
    ): FriendRequestDTO {
        val accept = request["accept"] ?: throw IllegalArgumentException("Missing accept")
        return friendRequestService.handleFriendRequest(requestId, accept).toDTO()
    }

    @GetMapping("/pending/{userId}")
    fun getPendingRequests(@PathVariable userId: Long): List<FriendRequestDTO> {
        return friendRequestService.getPendingRequests(userId).map { it.toDTO() }
    }
} 