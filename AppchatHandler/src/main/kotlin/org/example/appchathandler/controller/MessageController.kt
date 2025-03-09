package org.example.appchathandler.controller

import org.example.appchathandler.dto.*
import org.example.appchathandler.service.MessageService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.core.io.Resource
import java.io.File
import org.springframework.web.multipart.MultipartFile
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.example.appchathandler.repository.MessageRepository

@RestController
@RequestMapping("/api/messages")
@CrossOrigin
class MessageController(
    private val messageService: MessageService,
    private val messageRepository: MessageRepository,
    @Value("\${file.upload.dir}") private val uploadDir: String
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
    ): ResponseEntity<DeleteMessageResponse> {
        val message = messageService.findById(messageId) ?: return ResponseEntity.notFound().build()

        // 先标记消息为该用户已删除，并检查是否所有用户都已删除
        val allDeleted = messageService.markMessageAsDeleted(messageId, userId)

        return if (allDeleted) {
            // 如果所有用户都删除了这条消息，则彻底删除
            messageService.deleteMessageCompletely(messageId)
            ResponseEntity.ok(DeleteMessageResponse(true))
        } else {
            ResponseEntity.ok(DeleteMessageResponse(false))
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

    @GetMapping("/video/{filename}", produces = ["video/mp4"])
    fun getVideo(@PathVariable filename: String): ResponseEntity<Resource> {
        val file = File("uploads/videos/$filename")
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        val resource = FileSystemResource(file)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.name}\"")
            .contentLength(file.length())
            .contentType(MediaType.parseMediaType("video/mp4"))
            .body(resource)
    }

    @PostMapping("/files/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("senderId") senderId: Long
    ): ResponseEntity<FileDTO> {
        try {
            println("⭐ Starting file upload for user: $senderId")
            
            // 确保上传目录存在
            val uploadDirectory = File(System.getProperty("user.dir"), uploadDir)
            if (!uploadDirectory.exists()) {
                uploadDirectory.mkdirs()
                uploadDirectory.setReadable(true, false)
                uploadDirectory.setWritable(true, false)
                println("✅ Created upload directory: ${uploadDirectory.absolutePath}")
            }

            // 生成唯一文件名
            val originalFilename = file.originalFilename ?: "unknown"
            val extension = originalFilename.substringAfterLast('.', "")
            val uniqueFilename = "${UUID.randomUUID()}.$extension"
            
            // 保存文件
            val uploadedFile = File(uploadDirectory, uniqueFilename)
            file.transferTo(uploadedFile)
            uploadedFile.setReadable(true, false)
            println("✅ File saved to: ${uploadedFile.absolutePath}")

            // 构建文件URL
            val fileUrl = "/api/files/$uniqueFilename"
            println("✅ File URL generated: $fileUrl")

            return ResponseEntity.ok(FileDTO(
                id = 0,
                filename = originalFilename,
                url = fileUrl,
                size = file.size,
                contentType = file.contentType ?: "application/octet-stream"
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ File upload failed: ${e.message}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileDTO(
                    id = 0,
                    filename = "",
                    url = "",
                    size = 0,
                    contentType = "",
                    errorMessage = e.message ?: "Unknown error"
                ))
        }
    }

    @GetMapping("/files/{filename}")
    fun getFile(@PathVariable filename: String): ResponseEntity<Resource> {
        try {
            val uploadDirectory = File(System.getProperty("user.dir"), uploadDir)
            val file = File(uploadDirectory, filename)
            
            if (!file.exists()) {
                println("❌ File not found: ${file.absolutePath}")
                return ResponseEntity.notFound().build()
            }

            println("✅ Serving file: ${file.absolutePath}")
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(FileSystemResource(file))
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to serve file: ${e.message}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/sessions")
    fun getMessageSessions(@RequestParam userId: Long): ResponseEntity<List<MessageSessionInfo>> {
        return try {
            val sessions = messageService.getMessageSessions(userId)
            ResponseEntity.ok(sessions)
        } catch (e: Exception) {
            e.printStackTrace() // 打印错误堆栈以便调试
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/read")
    fun markSessionAsRead(
        @RequestParam userId: Long,
        @RequestParam partnerId: Long,
        @RequestParam type: String
    ): ResponseEntity<Void> {
        return try {
            println("📬 Marking messages as read: userId=$userId, partnerId=$partnerId, type=$type")
            when (type.uppercase()) {
                "GROUP" -> messageService.markGroupMessagesAsRead(userId, partnerId)
                "PRIVATE" -> messageService.markPrivateMessagesAsRead(userId, partnerId)
                else -> throw IllegalArgumentException("Invalid type: $type")
            }
            println("✅ Successfully marked messages as read")
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            println("❌ Error marking messages as read: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
} 