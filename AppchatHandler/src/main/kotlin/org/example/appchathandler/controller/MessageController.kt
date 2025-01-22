package org.example.appchathandler.controller

import org.example.appchathandler.dto.MessageDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.service.MessageService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime
import org.springframework.http.HttpStatus

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
    ): ResponseEntity<List<MessageDTO>> {
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
        @RequestParam("userId") userId: Long,
        @RequestParam("otherId") otherId: Long
    ): ResponseEntity<List<MessageDTO>> {
        return try {
            val messages = messageService.getPrivateMessages(userId, otherId)
            ResponseEntity.ok(messages)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserMessages(@PathVariable userId: Long): ResponseEntity<List<MessageDTO>> {
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
    ): ResponseEntity<List<MessageDTO>> {
        return try {
            val messages = messageService.searchMessages(userId, keyword)
            ResponseEntity.ok(messages.map { it.toDTO() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{messageId}")
    fun deleteMessage(
        @PathVariable messageId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Unit> {
        return try {
            messageService.deleteMessage(messageId, userId)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/all")
    fun deleteAllMessages(
        @RequestParam userId: Long,
        @RequestParam otherId: Long
    ): ResponseEntity<Unit> {
        messageService.deleteAllMessages(userId, otherId)
        return ResponseEntity.ok().build()
    }
} 