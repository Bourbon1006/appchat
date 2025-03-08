package org.example.appchathandler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageReadStatus

@Repository
interface MessageReadStatusRepository : JpaRepository<MessageReadStatus, Long> {
    fun findByUserIdAndMessageId(userId: Long, messageId: Long): MessageReadStatus?
    
    @Query("""
        SELECT COUNT(m) FROM Message m 
        LEFT JOIN MessageReadStatus mrs ON mrs.message = m AND mrs.user.id = :userId
        WHERE mrs IS NULL 
        AND m.receiver.id = :userId
        AND (
            (m.type = 'PRIVATE' AND m.sender.id = :partnerId)
            OR 
            (m.type = 'GROUP' AND m.group.id = :partnerId)
        )
    """)
    fun countUnreadMessages(userId: Long, partnerId: Long): Int

    @Query("""
        SELECT m FROM Message m 
        LEFT JOIN MessageReadStatus mrs ON mrs.message = m AND mrs.user.id = :userId
        WHERE mrs IS NULL 
        AND m.receiver.id = :userId
        AND (
            (m.type = 'PRIVATE' AND m.sender.id = :partnerId)
            OR 
            (m.type = 'GROUP' AND m.group.id = :partnerId)
        )
    """)
    fun findUnreadMessages(userId: Long, partnerId: Long): List<Message>
} 