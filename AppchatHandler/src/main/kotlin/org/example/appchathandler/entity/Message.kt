package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "messages")
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var content: String,

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    var receiver: User? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    var group: Group? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type")
    var type: MessageType = MessageType.TEXT,

    @Column(name = "file_url")
    var fileUrl: String? = null,

    @Column(name = "is_read")
    var isRead: Boolean = false
) {
    constructor() : this(
        content = "",
        sender = User()
    )
}