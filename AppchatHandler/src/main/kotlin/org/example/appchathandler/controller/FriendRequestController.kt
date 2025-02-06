package org.example.appchathandler.controller

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.service.FriendRequestService
import org.springframework.http.ResponseEntity
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
        val timestamp: String
    )

    private fun FriendRequest.toDTO() = FriendRequestDTO(
        id = id,
        sender = UserDTO(
            id = sender.id,
            username = sender.username,
            nickname = sender.nickname,
            avatarUrl = sender.avatarUrl,
            isOnline = sender.isOnline
        ),
        receiver = UserDTO(
            id = receiver.id,
            username = receiver.username,
            nickname = receiver.nickname,
            avatarUrl = receiver.avatarUrl,
            isOnline = receiver.isOnline
        ),
        status = status.name,
        timestamp = timestamp.toString()
    )

    @PostMapping
    fun sendFriendRequest(
        @RequestParam senderId: Long,
        @RequestParam receiverId: Long
    ): ResponseEntity<FriendRequestDTO> {
        return try {
            val request = friendRequestService.sendFriendRequest(senderId, receiverId)
            ResponseEntity.ok(request.toDTO())
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @PutMapping("/{requestId}")
    fun handleFriendRequest(
        @PathVariable requestId: Long,
        @RequestParam accept: Boolean
    ): ResponseEntity<FriendRequestDTO> {
        return try {
            val request = friendRequestService.handleFriendRequest(requestId, accept)
            ResponseEntity.ok(request.toDTO())
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/pending/{userId}")
    fun getPendingRequests(@PathVariable userId: Long): ResponseEntity<List<FriendRequestDTO>> {
        return try {
            val requests = friendRequestService.getPendingRequests(userId)
            ResponseEntity.ok(requests.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
} 