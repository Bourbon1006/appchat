package org.example.appchathandler.controller

import org.example.appchathandler.entity.User
import org.example.appchathandler.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.toDTO
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.io.File
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import java.awt.image.BufferedImage
import java.awt.Color
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    @Value("\${avatar.upload.dir}") private val avatarDir: String
) {

    data class UpdateUserRequest(
        val nickname: String? = null,
        val avatarUrl: String? = null
    )

    @GetMapping
    fun getAllUsers(): List<UserDTO> = userService.getAllUsers()

    @GetMapping("/online")
    fun getOnlineUsers(): List<UserDTO> = userService.getOnlineUsers()

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): ResponseEntity<UserDTO> {
        val user = userService.getUser(userId) ?: return ResponseEntity.notFound().build()
        
        return ResponseEntity.ok(UserDTO(
            id = user.id,
            username = user.username,
            nickname = user.nickname,
            avatarUrl = if (user.avatarUrl != null) "/api/users/${user.id}/avatar" else null,
            onlineStatus = user.onlineStatus ?: 0
        ))
    }

    @PutMapping("/{userId}")
    fun updateUser(
        @PathVariable userId: Long,
        @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserDTO> {
        return try {
            ResponseEntity.ok(userService.updateUser(userId, request))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{userId}/contacts")
    fun getUserContacts(@PathVariable userId: Long): ResponseEntity<List<UserDTO>> {
        return try {
            ResponseEntity.ok(userService.getUserContacts(userId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{userId}/contacts/{contactId}")
    fun addContact(
        @PathVariable userId: Long,
        @PathVariable contactId: Long
    ): ResponseEntity<UserDTO> {
        return try {
            ResponseEntity.ok(userService.addContact(userId, contactId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/search")
    fun searchUsers(@RequestParam keyword: String): ResponseEntity<List<UserDTO>> {
        println("Received search request with keyword: $keyword") // 添加日志
        val results = userService.searchUsers(keyword)
        println("Returning ${results.size} results") // 添加日志
        return ResponseEntity.ok(results)
    }

    @GetMapping("/{userId}/avatar")
    fun getAvatar(@PathVariable userId: Long): ResponseEntity<Resource> {
        val avatarDirectory = File(System.getProperty("user.dir"), avatarDir)
        val avatarFile = File(avatarDirectory, "$userId.jpg")
        
        if (!avatarFile.exists()) {
            // 使用预设的默认头像文件
            val defaultAvatarFile = File(avatarDirectory, "default.jpg")
            
            if (defaultAvatarFile.exists()) {
                val resource = FileSystemResource(defaultAvatarFile)
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource)
            } else {
                // 如果默认头像文件也不存在，才回退到生成头像
                println("⚠️ Default avatar file not found, generating one instead")
                try {
                    // 创建一个内存中的默认头像
                    val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
                    val g2d = image.createGraphics()
                    
                    g2d.color = Color(200, 200, 200)
                    g2d.fillRect(0, 0, 200, 200)
                    
                    g2d.color = Color(150, 150, 150)
                    g2d.fillOval(50, 50, 100, 100)
                    
                    g2d.dispose()
                    
                    // 转换为ByteArrayResource
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(image, "jpg", baos)
                    val imageBytes = baos.toByteArray()
                    
                    return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(ByteArrayResource(imageBytes))
                } catch (e: Exception) {
                    println("❌ Failed to create default avatar: ${e.message}")
                    return ResponseEntity.notFound().build()
                }
            }
        }
        
        // 返回用户自定义头像
        val resource = FileSystemResource(avatarFile)
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(resource)
    }

    @PostMapping("/{userId}/avatar")
    fun uploadAvatar(
        @PathVariable userId: Long,
        @RequestParam("avatar") avatar: MultipartFile
    ): ResponseEntity<UserDTO> {
        try {
            println("⭐ Uploading avatar for user: $userId")
            
            // 确保目录存在
            val avatarDirectory = File(System.getProperty("user.dir"), avatarDir)
            if (!avatarDirectory.exists()) {
                avatarDirectory.mkdirs()
                avatarDirectory.setReadable(true, false)
                avatarDirectory.setWritable(true, false)
            }
            
            // 保存头像文件
            val avatarFile = File(avatarDirectory, "$userId.jpg")
            avatar.transferTo(avatarFile)
            avatarFile.setReadable(true, false)
            println("✅ Avatar file saved to: ${avatarFile.absolutePath}")
            
            // 更新用户的头像URL并返回更新后的用户信息
            val avatarUrl = "/api/users/$userId/avatar"
            val updatedUser = userService.updateUserAvatar(userId, avatarUrl)
            println("✅ User avatar URL updated: $avatarUrl")
            
            return ResponseEntity.ok(updatedUser)
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to upload avatar: ${e.message}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PutMapping("/{userId}/status")
    fun updateOnlineStatus(
        @PathVariable userId: Long,
        @RequestParam status: Int
    ): ResponseEntity<Unit> {
        return try {
            userService.updateOnlineStatus(userId, status)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @PutMapping("/{userId}/nickname")
    fun updateNickname(
        @PathVariable userId: Long,
        @RequestBody request: UpdateNicknameRequest
    ): ResponseEntity<UserDTO> {
        return try {
            val user = userService.updateNickname(userId, request.nickname)
            ResponseEntity.ok(user.toDTO())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @PutMapping("/{userId}/password")
    fun updatePassword(
        @PathVariable userId: Long,
        @RequestBody request: UpdatePasswordRequest
    ): ResponseEntity<Unit> {
        return try {
            userService.updatePassword(userId, request.oldPassword, request.newPassword)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}

data class UpdateNicknameRequest(
    val nickname: String
)

data class UpdatePasswordRequest(
    val oldPassword: String,
    val newPassword: String
) 