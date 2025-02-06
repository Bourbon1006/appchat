package org.example.appchathandler.model

import jakarta.persistence.*
import java.time.LocalDateTime
import org.example.appchathandler.entity.User
import org.example.appchathandler.entity.Group

@Entity
data class Message(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne
    val sender: User,
    
    @ManyToOne
    var receiver: User? = null,
    
    @ManyToOne
    var group: Group? = null,
    
    val content: String,
    
    @Enumerated(EnumType.STRING)
    val type: MessageType,
    
    val fileUrl: String? = null,
    
    val timestamp: LocalDateTime = LocalDateTime.now(),
    
    @ElementCollection
    var deletedForUsers: Set<Long> = emptySet()
)

enum class MessageType {
    TEXT, FILE, IMAGE, VIDEO
} 