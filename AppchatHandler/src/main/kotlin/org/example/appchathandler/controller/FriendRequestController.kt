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
        val senderId: Long,
        val receiverId: Long
    )

    private fun FriendRequest.toDTO() = FriendRequestDTO(
        senderId = sender.id,
        receiverId = receiver.id
    )
    
    @PostMapping("/request")
    fun sendFriendRequest(@RequestBody request: FriendRequestDTO): ResponseEntity<FriendRequest> {
        return try {
            val sender = User(
                id = request.senderId,
                username = "",  // 这些值会被服务层覆盖
                password = "",
                onlineStatus = 0
            )
            
            val receiver = User(
                id = request.receiverId,
                username = "",  // 这些值会被服务层覆盖
                password = "",
                onlineStatus = 0
            )

            ResponseEntity.ok(friendRequestService.createFriendRequest(sender, receiver))
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
    fun getPendingRequests(@PathVariable userId: Long): ResponseEntity<List<UserDTO>> {
        return try {
            val requests = friendRequestService.getPendingRequests(userId)
            // 转换为发送者的完整 UserDTO 列表，包含 requestId
            val senderDTOs = requests.map { request -> 
                UserDTO(
                    id = request.sender.id,
                    username = request.sender.username,
                    nickname = request.sender.nickname,
                    avatarUrl = request.sender.avatarUrl,
                    onlineStatus = request.sender.onlineStatus,
                    isAdmin = false,
                    requestId = request.id  // 添加 requestId
                )
            }
            ResponseEntity.ok(senderDTOs)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().build()
        }
    }
}