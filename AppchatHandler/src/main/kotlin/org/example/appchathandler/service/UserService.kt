package org.example.appchathandler.service

import org.example.appchathandler.controller.UserController.UpdateUserRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {
    
    @Transactional
    fun setUserOnline(userId: Long, online: Boolean) {
        val user = getUser(userId)
        println("Before update - User ${user.username}(id=${user.id}): online=${user.online}")  // 添加日志
        user.online = online
        val savedUser = userRepository.save(user)
        println("After update - User ${savedUser.username}(id=${savedUser.id}): online=${savedUser.online}")  // 添加日志
    }

    fun getOnlineUsers(): List<User> {
        val users = userRepository.findByOnlineTrue()
        println("Found ${users.size} online users:")  // 添加日志
        users.forEach { user ->
            println("- ${user.username}(id=${user.id}): online=${user.online}")  // 添加日志
        }
        return users
    }

    fun getUser(userId: Long): User {
        val user = userRepository.findById(userId).orElseThrow {
            RuntimeException("User not found with id: $userId")
        }
        println("Retrieved user ${user.username}(id=${user.id}): online=${user.online}")  // 添加日志
        return user
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