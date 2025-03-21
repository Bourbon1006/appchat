package org.example.appchathandler.service

import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.UserRepository
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.*
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.security.crypto.password.PasswordEncoder

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    
    fun register(request: RegisterRequest): User {
        if (userRepository.findByUsername(request.username) != null) {
            throw IllegalArgumentException("Username already exists")
        }

        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            nickname = request.nickname,
            avatarUrl = request.avatarUrl,
            onlineStatus = 0  // 初始状态为离线
        )

        return userRepository.save(user)
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

    fun login(username: String, password: String): Map<String, Any> {
        // 获取用户
        val user = userRepository.findByUsername(username)
            ?: throw RuntimeException("用户名或密码错误")

        // 验证密码
        if (!BCrypt.checkpw(password, user.password)) {
            throw RuntimeException("用户名或密码错误")
        }

        // 生成 token 并返回
        return mapOf(
            "userId" to user.id,
            "username" to user.username,
            "token" to generateToken(user.id, user.username)
        )
    }
}

data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String? = null,
    val avatarUrl: String? = null
) 