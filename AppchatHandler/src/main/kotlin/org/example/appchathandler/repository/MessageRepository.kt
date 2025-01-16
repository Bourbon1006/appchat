package org.example.appchathandler.repository

import org.example.appchathandler.entity.Message
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository : JpaRepository<Message, Long> {
    
    @Query("SELECT m FROM Message m WHERE m.receiver IS NULL ORDER BY m.timestamp DESC")
    fun findByReceiverIsNullOrderByTimestampDesc(): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE (m.sender.id = :userId1 AND m.receiver.id = :userId2) 
           OR (m.sender.id = :userId2 AND m.receiver.id = :userId1)
        ORDER BY m.timestamp DESC
    """)
    fun findByUserMessages(userId1: Long, userId2: Long): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.receiver IS NULL 
           OR m.sender.id = :userId 
           OR m.receiver.id = :userId 
        ORDER BY m.timestamp DESC
    """)
    fun findRecentMessages(userId: Long): List<Message>
} 