package org.example.appchathandler.repository

import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    
    fun findByOnlineTrue(): List<User>

    fun findByUsernameContainingIgnoreCase(keyword: String): List<User>
} 