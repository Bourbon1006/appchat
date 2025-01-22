package org.example.appchathandler.service

import org.example.appchathandler.controller.UserController.UpdateUserRequest
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.toDTO

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    
    @Transactional
    fun setUserOnline(userId: Long, online: Boolean) {
        val user = getUser(userId)
        user.online = online
        userRepository.save(user)
    }

    fun getOnlineUsers(): List<UserDTO> {
        return userRepository.findByOnlineTrue().map { it.toDTO() }
    }

    fun getUser(userId: Long): User {
        return userRepository.findById(userId).orElseThrow { NoSuchElementException("用户不存在") }
    }

    fun getAllUsers(): List<UserDTO> {
        return userRepository.findAll().map { it.toDTO() }
    }

    @Transactional
    fun updateUser(userId: Long, request: UpdateUserRequest): UserDTO {
        val user = getUser(userId)
        request.nickname?.let { user.nickname = it }
        request.avatar?.let { user.avatar = it }
        return userRepository.save(user).toDTO()
    }

    @Transactional
    fun getUserContacts(userId: Long): List<UserDTO> {
        return getUser(userId).contacts.map { it.toDTO() }
    }

    @Transactional
    fun addContact(userId: Long, contactId: Long): UserDTO {
        val user = getUser(userId)
        val contact = getUser(contactId)
        user.contacts.add(contact)
        return userRepository.save(user).toDTO()
    }

    fun createUser(username: String, password: String): User {
        if (userRepository.findByUsername(username) != null) {
            throw IllegalArgumentException("用户名已存在")
        }
        
        val user = User(
            username = username,
            password = passwordEncoder.encode(password)
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
} 