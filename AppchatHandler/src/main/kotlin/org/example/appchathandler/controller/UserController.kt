package org.example.appchathandler.controller

import org.example.appchathandler.entity.User
import org.example.appchathandler.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.toDTO

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    data class UpdateUserRequest(
        val nickname: String? = null,
        val avatar: String? = null
    )

    @GetMapping
    fun getAllUsers(): List<UserDTO> = userService.getAllUsers()

    @GetMapping("/online")
    fun getOnlineUsers(): List<UserDTO> = userService.getOnlineUsers()

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): ResponseEntity<UserDTO> {
        return try {
            ResponseEntity.ok(userService.getUser(userId).toDTO())
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
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
    fun searchUsers(@RequestParam keyword: String): List<UserDTO> {
        return userService.getAllUsers().filter { 
            it.username.contains(keyword, ignoreCase = true) 
        }
    }
} 