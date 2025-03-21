package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "message_read_status")
data class MessageReadStatus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    @JsonIgnore // 防止 JSON 序列化时的循环引用
    val message: Message,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "read_time", nullable = false)
    val readTime: LocalDateTime = LocalDateTime.now()
) {
    // 重写 toString 方法，避免循环引用
    override fun toString(): String {
        return "MessageReadStatus(id=$id, messageId=${message.id}, userId=$userId, readTime=$readTime)"
    }
} 