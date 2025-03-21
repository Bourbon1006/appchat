package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import org.example.appchathandler.entity.MessageReadStatus
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "messages")
@NamedNativeQuery(
    name = "Message.findMessageSessions",
    query = """
        SELECT 
            m.id as id,
            CASE 
                WHEN m.type = 'PRIVATE' THEN 
                    CASE 
                        WHEN m.sender_id = :userId THEN m.receiver_id 
                        ELSE m.sender_id 
                    END
                ELSE m.group_id
            END as partner_id,
            CASE 
                WHEN m.type = 'PRIVATE' THEN 
                    CASE 
                        WHEN m.sender_id = :userId THEN r.username
                        ELSE s.username
                    END
                ELSE g.name
            END as partner_name,
            CASE 
                WHEN m.type = 'PRIVATE' THEN 
                    CASE 
                        WHEN m.sender_id = :userId THEN r.avatar_url
                        ELSE s.avatar_url
                    END
                ELSE g.avatar_url
            END as partner_avatar,
            m.content as last_message,
            m.timestamp as last_message_time,
            m.type as type
        FROM messages m
        LEFT JOIN users s ON m.sender_id = s.id
        LEFT JOIN users r ON m.receiver_id = r.id
        LEFT JOIN `groups` g ON m.group_id = g.id
        WHERE m.sender_id = :userId 
            OR m.receiver_id = :userId 
            OR m.group_id IN (SELECT group_id FROM group_members WHERE user_id = :userId)
        GROUP BY m.id
        ORDER BY m.timestamp DESC
    """,
    resultSetMapping = "MessageSessionInfoMapping"
)
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    val sender: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    val receiver: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: Group? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: MessageType = MessageType.TEXT,

    @Column(name = "file_url")
    val fileUrl: String? = null,

    @Column(name = "timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now(),

    @ElementCollection
    @CollectionTable(name = "message_deletions", joinColumns = [JoinColumn(name = "message_id")])
    @Column(name = "user_id")
    var deletedForUsers: MutableSet<Long> = mutableSetOf(),

    @OneToMany(mappedBy = "message", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonIgnore
    val readStatuses: MutableSet<MessageReadStatus> = mutableSetOf()
) {
    override fun toString(): String {
        return "Message(id=$id, content=$content, sender=${sender.id}, receiver=${receiver?.id}, group=${group?.id}, type=$type, timestamp=$timestamp)"
    }

    fun addReadStatus(user: User): MessageReadStatus {
        val readStatus = MessageReadStatus(
            message = this,
            userId = user.id,
            readTime = LocalDateTime.now()
        )
        return readStatus
    }

    fun markAsRead(user: User) {
        addReadStatus(user)
    }

    fun isReadBy(user: User): Boolean {
        return readStatuses.any { it.userId == user.id }
    }
}