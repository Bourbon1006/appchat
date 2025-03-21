package org.example.appchathandler.controller

import org.example.appchathandler.service.AuthService
import org.example.appchathandler.service.RegisterRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    data class LoginRequest(
        val username: String,
        val password: String
    )

    data class RegisterRequestDTO(
        val username: String,
        val password: String,
        val nickname: String? = null,
        val avatarUrl: String? = null
    )

    @PostMapping("/register")
    fun register(@RequestBody registerRequest: RegisterRequestDTO): ResponseEntity<Any> {
        return try {
            val request = RegisterRequest(
                username = registerRequest.username,
                password = registerRequest.password,
                nickname = registerRequest.nickname,
                avatarUrl = registerRequest.avatarUrl
            )
            val user = authService.register(request)
            ResponseEntity.ok(mapOf(
                "userId" to user.id,
                "username" to user.username
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf(
                "error" to e.message
            ))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Registration failed"
            ))
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: LoginRequest): ResponseEntity<Any> {
        return try {
            val result = authService.login(loginRequest.username, loginRequest.password)
            ResponseEntity.ok(result)
        } catch (e: RuntimeException) {
            ResponseEntity.badRequest().body(mapOf(
                "error" to e.message
            ))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Login failed"
            ))
        }
    }
} 