package org.example.appchathandler.service

import org.example.appchathandler.dto.AuthResponse
import org.springframework.stereotype.Service
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.Date

@Service
class AuthServiceImpl(private val userService: UserService) : AuthService {
    
    override fun login(username: String, password: String): Map<String, Any> {
        val user = userService.validateUser(username, password)
        val token = generateToken(user.id, user.username)
        return mapOf(
            "token" to token,
            "userId" to user.id,
            "username" to user.username
        )
    }

    override fun register(username: String, password: String, email: String): Map<String, Any> {
        val user = userService.createUser(username, password)
        val token = generateToken(user.id, user.username)
        return mapOf(
            "token" to token,
            "userId" to user.id,
            "username" to user.username
        )
    }

    private fun generateToken(userId: Long, username: String): String {
        val secretKey = "your-256-bit-secret" // 使用实际的密钥
        val expirationTime = 1000 * 60 * 60 // 1小时

        return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationTime))
            .signWith(SignatureAlgorithm.HS256, secretKey.toByteArray())
            .compact()
    }
} 