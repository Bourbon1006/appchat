package org.example.appchathandler.controller

import org.example.appchathandler.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    data class LoginRequest(
        val username: String = "",
        val password: String = ""
    )

    data class RegisterRequest(
        val username: String = "",
        val password: String = ""
    )

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, Any>> {
        val response = authService.login(request.username, request.password)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Any> {
        return try {
            val user = authService.register(
                username = request.username,
                password = request.password
            )
            ResponseEntity.ok(user)
        } catch (e: DataIntegrityViolationException) {
            // 用户名重复的情况
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "用户名已存在"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "注册失败: ${e.message}"))
        }
    }
} 