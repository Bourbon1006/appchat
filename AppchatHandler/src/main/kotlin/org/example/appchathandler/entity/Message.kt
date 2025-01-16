package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "messages")
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "sender_id")
    val sender: User,

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    val receiver: User? = null,

    @Column(columnDefinition = "TEXT")
    val content: String,

    @Enumerated(EnumType.STRING)
    val type: MessageType,

    @Column(nullable = true)
    val fileUrl: String? = null,

    val timestamp: LocalDateTime = LocalDateTime.now(),

    var isRead: Boolean = false
) {
    constructor() : this(
        id = 0,
        sender = User(),
        content = "",
        type = MessageType.TEXT
    )
}