package org.example.appchathandler.controller

import org.example.appchathandler.dto.GroupDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.service.GroupService
import org.example.appchathandler.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.example.appchathandler.repository.GroupRepository

@RestController
@RequestMapping("/api/groups")
@CrossOrigin
class GroupController(
    private val groupService: GroupService,
    private val userService: UserService,
    private val groupRepository: GroupRepository
) {

    data class CreateGroupRequest(
        val name: String,
        val creatorId: Long,
        val memberIds: List<Long>
    )

    @PostMapping
    fun createGroup(@RequestBody request: CreateGroupRequest): ResponseEntity<GroupDTO> {
        return try {
            val group = groupService.createGroup(
                name = request.name,
                creatorId = request.creatorId,
                memberIds = request.memberIds
            )
            ResponseEntity.ok(group)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/{groupId}/members/{userId}")
    fun addMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<GroupDTO> {
        return try {
            ResponseEntity.ok(groupService.addMember(groupId, userId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    fun removeMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long
    ): ResponseEntity<GroupDTO> {
        return try {
            ResponseEntity.ok(groupService.removeMember(groupId, userId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserGroups(@PathVariable userId: Long): ResponseEntity<List<GroupDTO>> {
        return try {
            ResponseEntity.ok(groupService.getUserGroups(userId))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().build()
        }
    }
} 