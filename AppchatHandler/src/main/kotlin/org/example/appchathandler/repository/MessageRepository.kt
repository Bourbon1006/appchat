package org.example.appchathandler.repository

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import org.example.appchathandler.dto.MessageSessionInfo

@Repository
interface MessageRepository : JpaRepository<Message, Long> {
    @Query("""
        SELECT m FROM Message m 
        WHERE m.group = :group
        ORDER BY m.timestamp ASC
    """)
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
    fun findByUserMessages(@Param("user") user: User): List<Message>

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

    @Query("""
        SELECT m FROM Message m 
        WHERE :userId NOT IN (SELECT du FROM m.deletedForUsers du)
        AND (
            (m.sender.id = :userId AND m.receiver.id = :otherId)
            OR (m.sender.id = :otherId AND m.receiver.id = :userId)
        )
        ORDER BY m.timestamp ASC
    """)
    fun findMessagesBetweenUsers(
        @Param("userId") userId: Long,
        @Param("otherId") otherId: Long
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND :userId NOT IN (SELECT du FROM m.deletedForUsers du)
        ORDER BY m.timestamp DESC
    """)
    fun findGroupMessages(
        @Param("groupId") groupId: Long,
        @Param("userId") userId: Long
    ): List<Message>
    @Query("""
    SELECT CASE 
        WHEN COUNT(m) = 0 THEN false
        WHEN COALESCE(SIZE(m.deletedForUsers), 0) = 
            CASE 
                WHEN m.group IS NULL THEN 2  
                ELSE (
                    SELECT COUNT(gm) FROM Group g JOIN g.members gm WHERE g = m.group
                )  
            END 
        THEN true 
        ELSE false 
    END
    FROM Message m 
    WHERE m.id = :messageId
""")
    fun isMessageDeletedForAllUsers(@Param("messageId") messageId: Long): Boolean

    @Query(
        nativeQuery = true,
        value = """
    WITH RankedMessages AS (
        SELECT 
            m.*,
            s.username as sender_name,
            s.avatar_url as sender_avatar,
            r.username as receiver_name,
            r.avatar_url as receiver_avatar,
            g.name as group_name,
            g.avatar_url as group_avatar,
            ROW_NUMBER() OVER (
                PARTITION BY 
                    CASE 
                        WHEN m.group_id IS NULL THEN  -- 使用 group_id 是否为 NULL 来判断是否为私聊
                            CASE 
                                WHEN m.sender_id = :userId THEN m.receiver_id
                                ELSE m.sender_id
                            END
                        ELSE m.group_id
                    END
                ORDER BY m.timestamp DESC
            ) as rn
        FROM messages m
        LEFT JOIN users s ON m.sender_id = s.id
        LEFT JOIN users r ON m.receiver_id = r.id
        LEFT JOIN `groups` g ON m.group_id = g.id
        WHERE m.sender_id = :userId 
            OR m.receiver_id = :userId 
            OR m.group_id IN (SELECT group_id FROM group_members WHERE user_id = :userId)
    )
    SELECT 
        id as id,
        CASE 
            WHEN group_id IS NULL THEN  -- 使用 group_id 是否为 NULL 来判断是否为私聊
                CASE 
                    WHEN sender_id = :userId THEN receiver_id
                    ELSE sender_id
                END
            ELSE group_id
        END as partnerId,
        CASE 
            WHEN group_id IS NULL THEN 
                CASE 
                    WHEN sender_id = :userId THEN receiver_name
                    ELSE sender_name
                END
            ELSE group_name
        END as partnerName,
        CASE 
            WHEN group_id IS NULL THEN 
                CASE 
                    WHEN sender_id = :userId THEN 
                        COALESCE(receiver_avatar, '/api/users/' || receiver_id || '/avatar')
                    ELSE 
                        COALESCE(sender_avatar, '/api/users/' || sender_id || '/avatar')
                END
            ELSE COALESCE(group_avatar, '/api/groups/' || group_id || '/avatar')
        END as partnerAvatar,
        content as lastMessage,
        timestamp as lastMessageTime,
        CASE 
            WHEN group_id IS NULL THEN 'PRIVATE'
            ELSE 'GROUP'
        END as type
    FROM RankedMessages
    WHERE rn = 1
    ORDER BY timestamp DESC
""")
    fun findMessageSessions(@Param("userId") userId: Long): List<MessageSessionInfo>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND (
            (m.sender.id = :partnerId AND m.receiver.id = :userId)
            OR (m.sender.id = :userId AND m.receiver.id = :partnerId)
        )
        AND :userId NOT IN (SELECT u.id FROM m.readBy u)
        ORDER BY m.timestamp ASC
    """)
    fun findUnreadPrivateMessages(
        @Param("userId") userId: Long,
        @Param("partnerId") partnerId: Long
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND :userId NOT IN (SELECT u.id FROM m.readBy u)
        ORDER BY m.timestamp ASC
    """)
    fun findUnreadGroupMessages(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long
    ): List<Message>
}