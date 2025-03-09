package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "message_read_status")
data class MessageReadStatus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    val message: Message,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "read_time", nullable = false)
    val readTime: LocalDateTime = LocalDateTime.now()
) 