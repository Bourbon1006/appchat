package org.example.appchathandler.controller

import org.example.appchathandler.dto.AuthResponse
import org.example.appchathandler.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.Date

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

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return try {
            val user = userService.validateUser(request.username, request.password)
            val token = generateToken(user.id, user.username)
            ResponseEntity.ok(AuthResponse(
                token = token,
                userId = user.id,
                username = user.username
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return try {
            val user = userService.createUser(request.username, request.password)
            val token = generateToken(user.id, user.username)
            ResponseEntity.ok(AuthResponse(
                token = token,
                userId = user.id,
                username = user.username
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun generateToken(userId: Long, username: String): String {
        val secretKey = "your-256-bit-secret" // Replace with your actual secret key
        val expirationTime = 1000 * 60 * 60 // 1 hour

        return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationTime))
            .signWith(SignatureAlgorithm.HS256, secretKey.toByteArray())
            .compact()
    }
} 