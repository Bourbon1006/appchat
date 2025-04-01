package org.example.appchathandler.controller

import org.example.appchathandler.entity.FriendGroup
import org.example.appchathandler.entity.User
import org.example.appchathandler.service.FriendGroupService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/friend-groups")
class FriendGroupController(private val friendGroupService: FriendGroupService) {

    @PostMapping
    fun createFriendGroup(
        @RequestParam userId: Long,
        @RequestParam name: String
    ): ResponseEntity<FriendGroup> {
        val friendGroup = friendGroupService.createFriendGroup(userId, name)
        return ResponseEntity.ok(friendGroup)
    }

    @DeleteMapping("/{groupId}")
    fun deleteFriendGroup(
        @PathVariable groupId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Unit> {
        friendGroupService.deleteFriendGroup(groupId, userId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/{groupId}/name")
    fun updateFriendGroupName(
        @PathVariable groupId: Long,
        @RequestParam userId: Long,
        @RequestParam newName: String
    ): ResponseEntity<FriendGroup> {
        val updatedGroup = friendGroupService.updateFriendGroupName(groupId, userId, newName)
        return ResponseEntity.ok(updatedGroup)
    }

    @PostMapping("/{groupId}/members/{friendId}")
    fun addFriendToGroup(
        @PathVariable groupId: Long,
        @PathVariable friendId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<FriendGroup> {
        val updatedGroup = friendGroupService.addFriendToGroup(groupId, userId, friendId)
        return ResponseEntity.ok(updatedGroup)
    }

    @DeleteMapping("/{groupId}/members/{friendId}")
    fun removeFriendFromGroup(
        @PathVariable groupId: Long,
        @PathVariable friendId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<FriendGroup> {
        val updatedGroup = friendGroupService.removeFriendFromGroup(groupId, userId, friendId)
        return ResponseEntity.ok(updatedGroup)
    }

    @GetMapping
    fun getFriendGroups(@RequestParam userId: Long): ResponseEntity<List<FriendGroup>> {
        val groups = friendGroupService.getFriendGroups(userId)
        return ResponseEntity.ok(groups)
    }

    @GetMapping("/{groupId}/members")
    fun getFriendGroupMembers(
        @PathVariable groupId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Set<User>> {
        val members = friendGroupService.getFriendGroupMembers(groupId, userId)
        return ResponseEntity.ok(members)
    }
}