package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import org.example.appchathandler.entity.MessageReadStatus

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    val receiver: User?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: Group?,

    @Column(nullable = false)
    val content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: MessageType = MessageType.TEXT,

    @Column(name = "file_url")
    val fileUrl: String?,

    @Column(nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "message", cascade = [CascadeType.ALL], orphanRemoval = true)
    val readStatuses: MutableSet<MessageReadStatus> = mutableSetOf(),

    @ElementCollection
    @CollectionTable(
        name = "message_deleted_users",
        joinColumns = [JoinColumn(name = "message_id")]
    )
    @Column(name = "user_id")
    var deletedForUsers: MutableSet<Long> = mutableSetOf(),

    @ManyToMany
    @JoinTable(
        name = "message_read_by",
        joinColumns = [JoinColumn(name = "message_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var readBy: MutableSet<User> = mutableSetOf()
) {
    fun addReadStatus(user: User) {
        readStatuses.add(MessageReadStatus(message = this, user = user))
    }

    fun markAsRead(user: User) {
        readBy.add(user)
    }

    fun isReadBy(user: User): Boolean {
        return readBy.contains(user)
    }
}