package org.example.appchathandler.repository

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MessageRepository : JpaRepository<Message, Long> {
    fun findByGroupOrderByTimestampAsc(group: Group): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND ((m.sender = :user1 AND m.receiver = :user2) 
        OR (m.sender = :user2 AND m.receiver = :user1))
        ORDER BY m.timestamp ASC
    """)
    fun findByPrivateChat(user1: User, user2: User): List<Message>
} 