package org.example.appchathandler.controller

import org.example.appchathandler.service.AuthService
import org.springframework.http.ResponseEntity
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
        val password: String = "",
        val email: String = ""
    )

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, Any>> {
        val response = authService.login(request.username, request.password)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val response = authService.register(request.username, request.password, request.email)
        return ResponseEntity.ok(response)
    }
} 