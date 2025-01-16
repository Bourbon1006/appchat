package org.example.appchathandler.service

import org.example.appchathandler.controller.UserController.UpdateUserRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {
    
    fun getUser(userId: Long): User {
        return userRepository.findById(userId).orElseThrow {
            RuntimeException("User not found with id: $userId")
        }
    }

    fun setUserOnline(userId: Long, online: Boolean) {
        userRepository.findById(userId).ifPresent { user ->
            user.online = online
            userRepository.save(user)
        }
    }

    fun getOnlineUsers(): List<User> {
        return userRepository.findByOnlineTrue()
    }

    fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }

    @Transactional
    fun updateUser(userId: Long, request: UpdateUserRequest): User {
        val user = getUser(userId)
        request.nickname?.let { user.nickname = it }
        request.avatar?.let { user.avatar = it }
        return userRepository.save(user)
    }

    @Transactional
    fun getUserContacts(userId: Long): List<User> {
        return getUser(userId).contacts.toList()
    }

    @Transactional
    fun addContact(userId: Long, contactId: Long): User {
        val user = getUser(userId)
        val contact = getUser(contactId)
        user.contacts.add(contact)
        return userRepository.save(user)
    }

    fun createUser(username: String, password: String): User {
        if (userRepository.findByUsername(username) != null) {
            throw IllegalArgumentException("用户名已存在")
        }
        
        val user = User(
            username = username,
            password = hashPassword(password)
        )
        return userRepository.save(user)
    }

    fun authenticate(username: String, password: String): User {
        val user = userRepository.findByUsername(username)
            ?: throw IllegalArgumentException("用户不存在")
            
        if (user.password != hashPassword(password)) {
            throw IllegalArgumentException("密码错误")
        }
        
        return user
    }

    private fun hashPassword(password: String): String {
        // 实际应用中应该使用proper密码加密
        return password
    }
} 