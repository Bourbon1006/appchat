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

@Service
class AuthService(private val userRepository: UserRepository) {
    
    fun register(username: String, password: String): Map<String, Any> {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username) != null) {
            throw RuntimeException("用户名已存在")
        }

        // 对密码进行加密
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

        // 创建新用户
        val user = User(
            username = username,
            password = hashedPassword,  // 使用加密后的密码
            nickname = username,  // 默认昵称与用户名相同
            avatarUrl = null,    // 默认头像为空
            isOnline = false     // 默认离线
        )

        // 保存用户
        val savedUser = userRepository.save(user)

        // 返回用户信息和 token
        return mapOf(
            "userId" to savedUser.id,
            "username" to savedUser.username,
            "token" to generateToken(savedUser.id, savedUser.username)
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