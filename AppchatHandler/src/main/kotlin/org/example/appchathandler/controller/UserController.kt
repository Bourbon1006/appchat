package org.example.appchathandler.controller

import org.example.appchathandler.entity.User
import org.example.appchathandler.service.UserService
import org.example.appchathandler.service.FriendRequestService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val friendRequestService: FriendRequestService
) {

    @GetMapping
    fun getUsers(): List<UserDTO> {
        return userService.getOnlineUsers().map { it.toDTO() }
    }

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): UserDTO {
        return userService.getUser(userId).toDTO()
    }

    @PutMapping("/{userId}")
    fun updateUser(@PathVariable userId: Long, @RequestBody request: UpdateUserRequest): UserDTO {
        return userService.updateUser(userId, request).toDTO()
    }

    @GetMapping("/{userId}/contacts")
    fun getUserContacts(@PathVariable userId: Long): List<UserDTO> {
        return userService.getUserContacts(userId).map { it.toDTO() }
    }

    @PostMapping("/{userId}/contacts/{contactId}")
    fun addContact(
        @PathVariable userId: Long,
        @PathVariable contactId: Long
    ): UserDTO {
        return userService.addContact(userId, contactId).toDTO()
    }

    @GetMapping("/online")
    fun getOnlineUsers(): List<UserDTO> {
        return userService.getOnlineUsers().map { it.toDTO() }
    }

    @GetMapping("/all")
    fun getAllUsers(): List<UserDTO> {
        return userService.getAllUsers().map { it.toDTO() }
    }

    @GetMapping("/search")
    fun searchUsers(@RequestParam keyword: String): List<UserDTO> {
        return userService.searchUsers(keyword).map { it.toDTO() }
    }

    data class UpdateUserRequest(
        val nickname: String?,
        val avatar: String?
    )

    data class UserDTO(
        val id: Long,
        val username: String,
        val nickname: String?,
        val avatar: String?,
        val online: Boolean
    )

    private fun User.toDTO() = UserDTO(
        id = id,
        username = username,
        nickname = nickname,
        avatar = avatar,
        online = online
    )
} 