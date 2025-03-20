package org.example.appchathandler.controller

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.service.FriendRequestService
import org.example.appchathandler.websocket.ChatWebSocketHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/friends")
class FriendRequestController(
    private val friendRequestService: FriendRequestService,
    private val webSocketHandler: ChatWebSocketHandler
) {
    
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
    
    @PostMapping("/request")
    fun sendFriendRequest(
        @RequestParam senderId: Long,
        @RequestParam receiverId: Long
    ): ResponseEntity<Unit> {
        return try {
            val request = friendRequestService.sendFriendRequest(senderId, receiverId)
            // 通过 WebSocket 通知接收者
            webSocketHandler.sendFriendRequest(request)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/handle")
    fun handleFriendRequest(
        @RequestParam requestId: Long,
        @RequestParam accept: Boolean
    ): ResponseEntity<Unit> {
        return try {
            println("📝 收到好友请求处理: requestId=$requestId, accept=$accept")
            
            val request = friendRequestService.getFriendRequest(requestId)
            if (request == null) {
                println("❌ 未找到好友请求: $requestId")
                return ResponseEntity.notFound().build()
            }
            
            if (request.status.toString() != "PENDING") {
                println("⚠️ 请求状态不是 PENDING: ${request.status}")
                return ResponseEntity.badRequest().build()
            }
            
            val updatedRequest = friendRequestService.handleFriendRequest(requestId, accept)
            println("✅ 好友请求处理成功: ${updatedRequest.status}")
            
            // 通知相关用户
            webSocketHandler.notifyFriendRequestResult(updatedRequest)
            
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            println("❌ 处理好友请求失败: ${e.message}")
            e.printStackTrace()
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