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
import org.springframework.data.jpa.repository.Modifying

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
        WHERE m.group IS NULL
        AND ((m.sender.id = :userId AND m.receiver.id = :otherId) 
        OR (m.sender.id = :otherId AND m.receiver.id = :userId))
        AND :userId NOT MEMBER OF m.deletedForUsers
        ORDER BY m.timestamp ASC
    """)
    fun findMessagesBetweenUsers(
        @Param("userId") userId: Long,
        @Param("otherId") otherId: Long
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND :userId NOT MEMBER OF m.deletedForUsers
        ORDER BY m.timestamp ASC
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
    WITH RECURSIVE RankedMessages AS (
        SELECT m.*,
            s.username as sender_name, s.avatar_url as sender_avatar,
            r.username as receiver_name, r.avatar_url as receiver_avatar,
            g.name as group_name, g.avatar_url as group_avatar,
            ROW_NUMBER() OVER (PARTITION BY 
                CASE 
                    WHEN m.group_id IS NULL THEN 
                        CASE 
                            WHEN m.sender_id = :userId THEN m.receiver_id 
                            ELSE m.sender_id 
                        END
                    ELSE m.group_id 
                END
                ORDER BY m.timestamp DESC) as rn
        FROM messages m
        LEFT JOIN users s ON m.sender_id = s.id
        LEFT JOIN users r ON m.receiver_id = r.id
        LEFT JOIN `groups` g ON m.group_id = g.id
        WHERE m.sender_id = :userId 
            OR m.receiver_id = :userId 
            OR m.group_id IN (SELECT group_id FROM group_members WHERE user_id = :userId)
    ),
    UnreadCounts AS (
        SELECT 
            CASE 
                WHEN m.group_id IS NULL THEN 
                    CASE 
                        WHEN m.sender_id = :userId THEN m.receiver_id
                        ELSE m.sender_id
                    END
                ELSE m.group_id
            END as partner_id,
            CASE 
                WHEN m.group_id IS NULL THEN 'PRIVATE'
                ELSE 'GROUP'
            END as message_type,
            COUNT(*) as unread_count
        FROM messages m
        LEFT JOIN message_deleted_users mdf ON m.id = mdf.message_id AND mdf.user_id = :userId
        WHERE m.id NOT IN (
            SELECT mrb.message_id
            FROM message_read_status mrb
            WHERE mrb.user_id = :userId
        )
        AND mdf.message_id IS NULL  -- 确保消息没有被当前用户删除
        AND (
            (m.group_id IS NULL AND m.receiver_id = :userId)
            OR
            (m.group_id IS NOT NULL AND m.sender_id != :userId AND m.group_id IN (SELECT group_id FROM group_members WHERE user_id = :userId))
        )
        GROUP BY 
            CASE 
                WHEN m.group_id IS NULL THEN 
                    CASE 
                        WHEN m.sender_id = :userId THEN m.receiver_id
                        ELSE m.sender_id
                    END
                ELSE m.group_id
            END,
            CASE 
                WHEN m.group_id IS NULL THEN 'PRIVATE'
                ELSE 'GROUP'
            END
    )
    SELECT 
        m.id as id,
        CASE 
            WHEN m.group_id IS NULL THEN 
                CASE 
                    WHEN m.sender_id = :userId THEN m.receiver_id
                    ELSE m.sender_id 
                END
            ELSE m.group_id
        END as partnerId,
        CASE 
            WHEN m.group_id IS NULL THEN 
                CASE 
                    WHEN m.sender_id = :userId THEN receiver_name
                    ELSE sender_name
                END
            ELSE group_name
        END as partnerName,
        CASE 
            WHEN m.group_id IS NULL THEN 
                CASE 
                    WHEN m.sender_id = :userId THEN 
                        COALESCE(receiver_avatar, '/api/users/' || m.receiver_id || '/avatar')
                    ELSE 
                        COALESCE(sender_avatar, '/api/users/' || m.sender_id || '/avatar')
                END
            ELSE COALESCE(group_avatar, '/api/groups/' || m.group_id || '/avatar')
        END as partnerAvatar,
        m.content as lastMessage,
        m.timestamp as lastMessageTime,
        CASE 
            WHEN m.group_id IS NULL THEN 'PRIVATE'
            ELSE 'GROUP'
        END as type,
        COALESCE(uc.unread_count, 0) as unreadCount
    FROM RankedMessages m
    LEFT JOIN UnreadCounts uc ON uc.partner_id = 
        CASE 
            WHEN m.group_id IS NULL THEN 
                CASE 
                    WHEN m.sender_id = :userId THEN m.receiver_id
                    ELSE m.sender_id 
                END
            ELSE m.group_id
        END
        AND uc.message_type = 
        CASE 
            WHEN m.group_id IS NULL THEN 'PRIVATE'
            ELSE 'GROUP'
        END
    WHERE rn = 1
    ORDER BY m.timestamp DESC
""")
    fun findMessageSessions(@Param("userId") userId: Long): List<MessageSessionInfo>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND m.receiver.id = :userId
        AND m.sender.id = :partnerId
        AND :userId NOT IN (SELECT du FROM m.deletedForUsers du)
        AND m NOT IN (
            SELECT mrs.message 
            FROM MessageReadStatus mrs 
            WHERE mrs.userId = :userId
        )
        ORDER BY m.timestamp ASC
    """)
    fun findUnreadPrivateMessages(
        @Param("userId") userId: Long,
        @Param("partnerId") partnerId: Long
    ): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND m.sender.id <> :userId
        AND :userId NOT IN (SELECT du FROM m.deletedForUsers du)
        AND m NOT IN (
            SELECT mrs.message 
            FROM MessageReadStatus mrs 
            WHERE mrs.userId = :userId
        )
        ORDER BY m.timestamp ASC
    """)
    fun findUnreadGroupMessages(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long
    ): List<Message>

    @Modifying
    @Query("""
        DELETE FROM Message m 
        WHERE m.type = 'PRIVATE' 
        AND ((m.sender.id = :userId AND m.receiver.id = :friendId)
        OR (m.sender.id = :friendId AND m.receiver.id = :userId))
    """)
    fun deleteByPrivateChat(userId: Long, friendId: Long)

    @Modifying
    @Query("""
        DELETE FROM Message m 
        WHERE m.sender.id = :userId 
        AND m.receiver.id = :partnerId
    """)
    fun deleteByUserIdAndPartnerId(userId: Long, partnerId: Long)

    @Query("""
        SELECT m FROM Message m 
        WHERE (m.sender.id = :userId OR m.receiver.id = :userId)
        AND m.group IS NULL
        AND m.id IN (
            SELECT MAX(m2.id) FROM Message m2 
            WHERE (m2.sender.id = :userId OR m2.receiver.id = :userId)
            AND m2.group IS NULL
            AND :userId NOT MEMBER OF m2.deletedForUsers
            GROUP BY 
                CASE 
                    WHEN m2.sender.id = :userId THEN m2.receiver.id 
                    ELSE m2.sender.id 
                END
        )
    """)
    fun findLatestPrivateMessagesByUser(@Param("userId") userId: Long): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id IN (SELECT g.id FROM Group g JOIN g.members m WHERE m.id = :userId)
        AND m.id IN (
            SELECT MAX(m2.id) FROM Message m2 
            WHERE m2.group.id IN (SELECT g2.id FROM Group g2 JOIN g2.members m2 WHERE m2.id = :userId)
            AND :userId NOT MEMBER OF m2.deletedForUsers
            GROUP BY m2.group.id
        )
    """)
    fun findLatestGroupMessagesByUser(@Param("userId") userId: Long): List<Message>

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND :userId NOT MEMBER OF m.deletedForUsers
        ORDER BY m.timestamp DESC
        LIMIT 1
    """)
    fun findLastGroupMessage(groupId: Long, userId: Long): Message?

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group.id = :groupId
        AND :userId NOT MEMBER OF m.deletedForUsers
        AND m.id != (
            SELECT m2.id FROM Message m2 
            WHERE m2.group.id = :groupId
            AND :userId NOT MEMBER OF m2.deletedForUsers
            ORDER BY m2.timestamp DESC
            LIMIT 1
        )
        ORDER BY m.timestamp DESC
        LIMIT 1
    """)
    fun findSecondLastGroupMessage(groupId: Long, userId: Long): Message?

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND ((m.sender.id = :userId AND m.receiver.id = :otherId) 
        OR (m.sender.id = :otherId AND m.receiver.id = :userId))
        AND :userId NOT MEMBER OF m.deletedForUsers
        ORDER BY m.timestamp DESC
        LIMIT 1
    """)
    fun findLastPrivateMessage(
        @Param("userId") userId: Long,
        @Param("otherId") otherId: Long
    ): Message?

    @Query("""
        SELECT m FROM Message m 
        WHERE m.group IS NULL 
        AND ((m.sender.id = :userId AND m.receiver.id = :otherId) 
        OR (m.sender.id = :otherId AND m.receiver.id = :userId))
        AND :userId NOT MEMBER OF m.deletedForUsers
        AND m.id != (
            SELECT MAX(m2.id) FROM Message m2 
            WHERE m2.group IS NULL 
            AND ((m2.sender.id = :userId AND m2.receiver.id = :otherId) 
            OR (m2.sender.id = :otherId AND m2.receiver.id = :userId))
            AND :userId NOT MEMBER OF m2.deletedForUsers
        )
        ORDER BY m.timestamp DESC
        LIMIT 1
    """)
    fun findSecondLastPrivateMessage(
        @Param("userId") userId: Long,
        @Param("otherId") otherId: Long
    ): Message?
}
