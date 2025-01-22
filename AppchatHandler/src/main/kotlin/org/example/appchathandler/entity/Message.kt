package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "messages")
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val content: String,

    val timestamp: LocalDateTime = LocalDateTime.now(),

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

    val fileUrl: String? = null,

    @ElementCollection
    @CollectionTable(name = "message_deleted_users")
    val deletedForUsers: MutableSet<Long> = mutableSetOf()
) {
    constructor() : this(
        content = "",
        sender = User()
    )
}