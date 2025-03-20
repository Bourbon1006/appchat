package org.example.appchathandler.controller

import org.example.appchathandler.dto.MomentDTO
import org.example.appchathandler.dto.MomentCommentDTO
import org.example.appchathandler.service.MomentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/moments")
class MomentController(private val momentService: MomentService) {

    data class CreateMomentRequest(
        val content: String,
        val imageUrl: String?
    )

    data class CreateCommentRequest(
        val content: String
    )

    @PostMapping
    fun createMoment(
        @RequestParam userId: Long,
        @RequestBody request: CreateMomentRequest
    ): ResponseEntity<MomentDTO> {
        val moment = momentService.createMoment(userId, request.content, request.imageUrl)
        return ResponseEntity.ok(moment)
    }

    @GetMapping("/friends")
    fun getFriendMoments(@RequestParam userId: Long): ResponseEntity<List<MomentDTO>> {
        val moments = momentService.getFriendMoments(userId)
        return ResponseEntity.ok(moments)
    }

    @GetMapping("/user/{userId}")
    fun getUserMoments(@PathVariable userId: Long): ResponseEntity<List<MomentDTO>> {
        val moments = momentService.getUserMoments(userId)
        return ResponseEntity.ok(moments)
    }

    @PostMapping("/{momentId}/like")
    fun likeMoment(
        @PathVariable momentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Unit> {
        momentService.likeMoment(momentId, userId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{momentId}/like")
    fun unlikeMoment(
        @PathVariable momentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Unit> {
        momentService.unlikeMoment(momentId, userId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{momentId}/comments")
    fun addComment(
        @PathVariable momentId: Long,
        @RequestParam userId: Long,
        @RequestBody request: CreateCommentRequest
    ): ResponseEntity<MomentCommentDTO> {
        val comment = momentService.addComment(momentId, userId, request.content)
        return ResponseEntity.ok(comment)
    }

    @DeleteMapping("/{momentId}")
    @Transactional
    fun deleteMoment(
        @PathVariable momentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Unit> {
        return try {
            println("⭐ Deleting moment: $momentId for user: $userId")
            momentService.deleteMoment(momentId, userId)
            println("✅ Moment deleted successfully")
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            println("❌ Failed to delete moment: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
} 