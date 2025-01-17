package org.example.appchathandler.controller

import org.example.appchathandler.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    data class LoginRequest(
        val username: String,
        val password: String
    )

    data class RegisterRequest(
        val username: String,
        val password: String
    )

    data class AuthResponse(
        val userId: Long,
        val username: String,
        val token: String? = null
    )

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return try {
            val user = userService.validateUser(request.username, request.password)
            ResponseEntity.ok(AuthResponse(
                userId = user.id,
                username = user.username
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(null)
        }
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return try {
            val user = userService.createUser(request.username, request.password)
            ResponseEntity.ok(AuthResponse(
                userId = user.id,
                username = user.username
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(null)
        }
    }
} 