package org.example.appchathandler.controller

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.example.appchathandler.service.FriendService
import org.example.appchathandler.websocket.ChatWebSocketHandler

@RestController
@RequestMapping("/api/friends")
class FriendController(
    private val friendService: FriendService,
    private val webSocketHandler: ChatWebSocketHandler
) {
    // ... 其他方法 ...

    @DeleteMapping
    fun deleteFriend(
        @RequestParam userId: Long,
        @RequestParam friendId: Long
    ): ResponseEntity<Unit> {
        friendService.deleteFriend(userId, friendId)
        
        // 通知双方好友关系已删除
        webSocketHandler.notifyFriendDeleted(userId, friendId)
        webSocketHandler.notifyFriendDeleted(friendId, userId)
        
        return ResponseEntity.ok().build()
    }
} 