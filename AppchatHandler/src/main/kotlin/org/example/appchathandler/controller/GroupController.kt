package org.example.appchathandler.controller

import org.example.appchathandler.dto.GroupCreateRequest
import org.example.appchathandler.dto.GroupDTO
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.service.GroupService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Value
import java.io.File
import org.example.appchathandler.dto.GroupMemberDTO
import org.example.appchathandler.entity.GroupMember
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService,
    @Value("\${group.avatar.upload.dir}") private val groupAvatarDir: String
) {

    @GetMapping("/{groupId}")
    fun getGroupDetails(@PathVariable groupId: Long): ResponseEntity<GroupDTO> {
        return try {
            val group = groupService.getGroupById(groupId)
            ResponseEntity.ok(group)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping
    fun createGroup(@RequestBody request: GroupCreateRequest): ResponseEntity<GroupDTO> {
        return try {
            val newGroup = groupService.createGroup(request)
            ResponseEntity.status(HttpStatus.CREATED).body(newGroup)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().build()
        }
    }
    
    @PutMapping("/{groupId}/name")
    fun updateGroupName(
        @PathVariable groupId: Long, 
        @RequestParam name: String
    ): ResponseEntity<GroupDTO> {
        return try {
            val updatedGroup = groupService.updateGroupName(groupId, name)
            ResponseEntity.ok(updatedGroup)
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
    
    @PostMapping("/{groupId}/avatar")
    fun updateGroupAvatar(
        @PathVariable groupId: Long,
        @RequestParam("avatar") file: MultipartFile
    ): ResponseEntity<String> {
        try {
            val avatarDirectory = File(System.getProperty("user.dir"), groupAvatarDir)
            if (!avatarDirectory.exists()) {
                avatarDirectory.mkdirs()
            }

            val avatarFile = File(avatarDirectory, "$groupId.jpg")
            file.transferTo(avatarFile)

            // 更新数据库中的头像URL
            val avatarUrl = "/api/groups/$groupId/avatar"
            groupService.updateGroupAvatar(groupId, avatarUrl)

            return ResponseEntity.ok("头像上传成功")
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("头像上传失败: ${e.message}")
        }
    }
    
    @GetMapping("/{groupId}/members")
    fun getGroupMembers(@PathVariable groupId: Long): ResponseEntity<List<UserDTO>> {
        return try {
            val members = groupService.getGroupMembers(groupId)
            ResponseEntity.ok(members)
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
    
    @PostMapping("/{groupId}/members/{userId}")
    fun addMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<GroupMemberDTO> {
        return try {
            val member = groupService.addMember(groupId, userId)
            ResponseEntity.ok(GroupMemberDTO(
                userId = member.user.id,
                groupId = member.group.id,
                isAdmin = member.isAdmin,
                joinedAt = member.joinedAt
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            when (e) {
                is RuntimeException -> ResponseEntity.notFound().build()
                else -> ResponseEntity.badRequest().build()
            }
        }
    }
    
    @DeleteMapping("/{groupId}/members/{memberId}")
    fun removeMember(
        @PathVariable groupId: Long,
        @PathVariable memberId: Long
    ): ResponseEntity<Void> {
        return try {
            groupService.removeMember(groupId, memberId)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
    
    @PostMapping("/{groupId}/leave")
    fun leaveGroup(
        @PathVariable groupId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Void> {
        return try {
            groupService.removeMember(groupId, userId)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
    
    @GetMapping("/user/{userId}")
    fun getGroupsByUserId(@PathVariable userId: Long): ResponseEntity<List<GroupDTO>> {
        return try {
            val groups = groupService.getGroupsByUserId(userId)
            ResponseEntity.ok(groups)
        } catch (e: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{groupId}/avatar")
    fun getGroupAvatar(@PathVariable groupId: Long): ResponseEntity<Resource> {
        val avatarDirectory = File(System.getProperty("user.dir"), groupAvatarDir)
        val avatarFile = File(avatarDirectory, "$groupId.jpg")
        
        if (!avatarFile.exists()) {
            // 使用默认的群组头像
            val defaultAvatarFile = File(avatarDirectory, "default.jpg")
            
            if (defaultAvatarFile.exists()) {
                val resource = org.springframework.core.io.FileSystemResource(defaultAvatarFile)
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource)
            } else {
                return ResponseEntity.notFound().build()
            }
        }
        
        val resource = org.springframework.core.io.FileSystemResource(avatarFile)
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(resource)
    }
} 