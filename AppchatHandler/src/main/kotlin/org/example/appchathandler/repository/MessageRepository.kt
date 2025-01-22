package org.example.appchathandler.repository

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MessageRepository : JpaRepository<Message, Long> {
    fun findByGroupOrderByTimestampAsc(group: Group): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND (
            (m.sender.id = :user1Id AND m.receiver.id = :user2Id) 
            OR (m.sender.id = :user2Id AND m.receiver.id = :user1Id)
        )
        ORDER BY m.timestamp ASC
    """)
    fun findByPrivateChat(
        @Param("user1Id") user1Id: Long,
        @Param("user2Id") user2Id: Long
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.sender = :user 
        OR m.receiver = :user 
        OR (m.group IS NOT NULL AND :user MEMBER OF m.group.members)
        ORDER BY m.timestamp DESC
    """)
    fun findByUserMessages(user: User): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE (m.sender = :user OR m.receiver = :user OR :user MEMBER OF m.group.members)
        AND m.timestamp BETWEEN :startDate AND :endDate
        ORDER BY m.timestamp DESC
    """)
    fun findByDateRange(
        user: User,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE (m.sender = :user OR m.receiver = :user OR :user MEMBER OF m.group.members)
        AND LOWER(m.content) LIKE LOWER(:keyword)
        ORDER BY m.timestamp DESC
    """)
    fun searchMessages(user: User, keyword: String): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND (
            (m.sender.id = :user1Id AND m.receiver.id = :user2Id) 
            OR (m.sender.id = :user2Id AND m.receiver.id = :user1Id)
        )
        AND m.timestamp BETWEEN :startTime AND :endTime
        ORDER BY m.timestamp ASC
    """)
    fun findByPrivateChatAndDateRange(
        @Param("user1Id") user1Id: Long,
        @Param("user2Id") user2Id: Long,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group = :group
        AND m.timestamp BETWEEN :startTime AND :endTime
        ORDER BY m.timestamp ASC
    """)
    fun findByGroupAndDateRange(
        @Param("group") group: Group,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<Message>
} 