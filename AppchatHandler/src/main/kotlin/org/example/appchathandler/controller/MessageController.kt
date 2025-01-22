package org.example.appchathandler.controller

import org.example.appchathandler.dto.ChatMessageDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.service.MessageService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/messages")
@CrossOrigin
class MessageController(
    private val messageService: MessageService
) {
    @GetMapping("/group/{groupId}")
    fun getGroupMessages(
        @PathVariable groupId: Long,
        @RequestParam(required = false) startTime: LocalDateTime?,
        @RequestParam(required = false) endTime: LocalDateTime?
    ): ResponseEntity<List<ChatMessageDTO>> {
        return try {
            val messages = if (startTime != null && endTime != null) {
                messageService.getGroupMessagesByDateRange(groupId, startTime, endTime)
            } else {
                messageService.getGroupMessages(groupId)
            }
            ResponseEntity.ok(messages.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/private")
    fun getPrivateMessages(
        @RequestParam user1Id: Long,
        @RequestParam user2Id: Long,
        @RequestParam(required = false) startTime: LocalDateTime?,
        @RequestParam(required = false) endTime: LocalDateTime?
    ): ResponseEntity<List<ChatMessageDTO>> {
        return try {
            val messages = if (startTime != null && endTime != null) {
                messageService.getPrivateMessagesByDateRange(user1Id, user2Id, startTime, endTime)
            } else {
                messageService.getPrivateMessages(user1Id, user2Id)
            }
            ResponseEntity.ok(messages.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserMessages(@PathVariable userId: Long): ResponseEntity<List<ChatMessageDTO>> {
        return try {
            val messages = messageService.getUserMessages(userId)
            ResponseEntity.ok(messages.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/search")
    fun searchMessages(
        @RequestParam userId: Long,
        @RequestParam keyword: String
    ): ResponseEntity<List<ChatMessageDTO>> {
        return try {
            val messages = messageService.searchMessages(userId, keyword)
            ResponseEntity.ok(messages.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
} 