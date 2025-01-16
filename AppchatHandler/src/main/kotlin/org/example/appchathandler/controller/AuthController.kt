package org.example.appchathandler.controller

import org.example.appchathandler.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    data class RegisterRequest(
        val username: String,
        val password: String
    )

    data class LoginRequest(
        val username: String,
        val password: String
    )

    data class AuthResponse(
        val token: String,
        val userId: Long,
        val username: String,
        val message: String? = null
    )

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): AuthResponse {
        return try {
            val user = userService.createUser(
                username = request.username,
                password = request.password
            )
            AuthResponse(
                token = generateToken(),
                userId = user.id,
                username = user.username,
                message = "注册成功"
            )
        } catch (e: Exception) {
            AuthResponse(
                token = "",
                userId = -1,
                username = "",
                message = e.message ?: "注册失败"
            )
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): AuthResponse {
        return try {
            val user = userService.authenticate(request.username, request.password)
            AuthResponse(
                token = generateToken(),
                userId = user.id,
                username = user.username,
                message = "登录成功"
            )
        } catch (e: Exception) {
            AuthResponse(
                token = "",
                userId = -1,
                username = "",
                message = e.message ?: "登录失败"
            )
        }
    }

    private fun generateToken(): String {
        return java.util.UUID.randomUUID().toString()
    }
} 