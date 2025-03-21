package org.example.appchathandler.service

import jakarta.persistence.EntityNotFoundException
import org.example.appchathandler.controller.UserController.UpdateUserRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.toDTO
import org.slf4j.LoggerFactory
import org.springframework.web.multipart.MultipartFile
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.beans.factory.annotation.Autowired

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val fileService: FileService
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    // 创建事件发布器
    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Transactional
    fun setUserOnline(userId: Long, status: Int) {
        val user = getUser(userId)
        user.onlineStatus = status
        userRepository.save(user)
    }

    fun getOnlineUsers(): List<UserDTO> {
        return userRepository.findByOnlineStatus(1).map { it.toDTO() }
    }

    fun getUser(userId: Long): User {
        return userRepository.findById(userId).orElseThrow { 
            NoSuchElementException("User not found with id: $userId")
        }
    }

    fun getAllUsers(): List<UserDTO> {
        return userRepository.findAll().map { it.toDTO() }
    }

    @Transactional
    fun updateUser(userId: Long, request: UpdateUserRequest): UserDTO {
        logger.info("Updating user $userId with request: $request")
        val user = getUser(userId)
        
        request.nickname?.let { 
            user.nickname = it
            logger.info("Updated nickname to: $it")
        }
        
        request.avatarUrl?.let { 
            user.avatarUrl = it
            logger.info("Updated avatarUrl to: $it")
        }
        
        val savedUser = userRepository.save(user)
        logger.info("User saved successfully")
        return savedUser.toDTO()
    }

    @Transactional
    fun getUserContacts(userId: Long): List<UserDTO> {
        val user = getUser(userId)
        return user.contacts.map { it.toDTO() }
    }

    @Transactional
    fun addContact(userId: Long, contactId: Long): UserDTO {
        val user = getUser(userId)
        val contact = getUser(contactId)
        user.contacts.add(contact)
        return userRepository.save(user).toDTO()
    }

    fun createUser(username: String, password: String): User {
        val user = User(
            username = username,
            password = passwordEncoder.encode(password),
            onlineStatus = 0
        )
        return userRepository.save(user)
    }

    fun getUserByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    fun validateUser(username: String, password: String): UserDTO {
        val user = getUserByUsername(username) ?: throw IllegalArgumentException("用户不存在")
        if (!passwordEncoder.matches(password, user.password)) {
            throw IllegalArgumentException("密码错误")
        }
        return user.toDTO()
    }

    fun searchUsers(keyword: String): List<UserDTO> {
        println("Searching users with keyword: $keyword")
        return userRepository.findByUsernameContainingIgnoreCase(keyword)
            .map { it.toDTO() }
            .also { println("Found ${it.size} users") }
    }

    fun updateAvatar(userId: Long, file: MultipartFile): UserDTO {
        println("⭐ Processing avatar update for user: $userId")
        
        try {
            val savedFile = fileService.saveFile(file)
            println("✅ File saved successfully: ${savedFile.url}")
            
            val user = userRepository.findById(userId).orElseThrow {
                println("❌ User not found: $userId")
                NoSuchElementException("User not found")
            }
            
            user.avatarUrl = savedFile.url
            val updatedUser = userRepository.save(user)
            println("✅ User avatar updated: ${updatedUser.avatarUrl}")
            
            return updatedUser.toDTO()
        } catch (e: Exception) {
            println("❌ Failed to update avatar: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Transactional
    fun updateUserAvatar(userId: Long, avatarUrl: String): UserDTO {
        val user = userRepository.findById(userId).orElseThrow {
            throw EntityNotFoundException("User not found")
        }
        user.avatarUrl = avatarUrl
        val savedUser = userRepository.save(user)
        return savedUser.toDTO()
    }

    @Transactional
    fun updateOnlineStatus(userId: Long, status: Int) {
        val user = userRepository.findById(userId).orElseThrow()
        user.onlineStatus = status
        userRepository.save(user)
        
        // 发布状态更新事件而不是直接调用 WebSocket
        eventPublisher.publishEvent(UserStatusUpdateEvent(this, userId, status))
    }

    @Transactional
    fun updateNickname(userId: Long, nickname: String): User {
        val user = userRepository.findById(userId).orElseThrow()
        user.nickname = nickname
        return userRepository.save(user)
    }

    @Transactional
    fun updatePassword(userId: Long, oldPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow()
        
        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.password)) {
            throw IllegalArgumentException("旧密码错误")
        }
        
        // 更新新密码
        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)
    }
}

// 创建状态更新事件类
class UserStatusUpdateEvent(
    source: Any,
    val userId: Long,
    val status: Int
) : ApplicationEvent(source) 