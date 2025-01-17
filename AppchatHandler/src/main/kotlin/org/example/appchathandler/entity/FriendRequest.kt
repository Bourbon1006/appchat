package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import org.example.appchathandler.entity.User

@Entity
@Table(name = "friend_requests")
class FriendRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    var sender: User,

    @ManyToOne
    var receiver: User,

    @Enumerated(EnumType.STRING)
    var status: RequestStatus = RequestStatus.PENDING,

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now()
)

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
} 