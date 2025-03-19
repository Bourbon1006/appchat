package org.example.appchathandler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageReadStatus

@Repository
interface MessageReadStatusRepository : JpaRepository<MessageReadStatus, Long> {
    fun findByUserIdAndMessageId(userId: Long, messageId: Long): MessageReadStatus?
    
    @Query("""
        SELECT COUNT(m) FROM Message m 
        LEFT JOIN MessageReadStatus mrs ON mrs.message = m AND mrs.userId = :userId
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
        LEFT JOIN MessageReadStatus mrs ON mrs.message = m AND mrs.userId = :userId
        WHERE mrs IS NULL 
        AND (
            (m.group IS NULL AND m.receiver.id = :userId AND m.sender.id = :partnerId)
            OR 
            (m.group IS NOT NULL AND m.group.id = :partnerId)
        )
        ORDER BY m.timestamp ASC
    """)
    fun findUnreadMessages(userId: Long, partnerId: Long): List<Message>

    @Query("""
        SELECT CASE WHEN COUNT(mrs) > 0 THEN true ELSE false END 
        FROM MessageReadStatus mrs 
        WHERE mrs.message.id = :messageId 
        AND mrs.userId = :userId
    """)
    fun isMessageReadByUser(messageId: Long, userId: Long): Boolean

    fun existsByMessageIdAndUserId(messageId: Long, userId: Long): Boolean

    @Modifying
    @Query("DELETE FROM MessageReadStatus mrs WHERE mrs.message.id = :messageId")
    fun deleteByMessageId(messageId: Long)
} 