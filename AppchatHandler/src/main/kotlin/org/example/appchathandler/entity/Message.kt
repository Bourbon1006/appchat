package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "messages")
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var content: String,

    @Column(name = "timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now(),

    @ManyToOne
    @JoinColumn(name = "sender_id")
    val sender: User,

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    val receiver: User? = null,

    @ManyToOne
    @JoinColumn(name = "group_id")
    val group: Group? = null,

    @Enumerated(EnumType.STRING)
    val type: MessageType = MessageType.TEXT,

    @Column(name = "file_url")
    val fileUrl: String? = null,

    @ElementCollection
    @CollectionTable(
        name = "message_deleted_users",
        joinColumns = [JoinColumn(name = "message_id")]
    )
    @Column(name = "user_id")
    var deletedForUsers: MutableSet<Long> = mutableSetOf()
) {
    constructor() : this(
        content = "",
        sender = User()
    )
}