package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.io.Serializable

@Entity
@Table(name = "group_members")
data class GroupMember(
    @EmbeddedId
    val id: GroupMemberId = GroupMemberId(),
    
    @MapsId("groupId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: Group,
    
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(name = "joined_at")
    val joinedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "is_admin")
    val isAdmin: Boolean = false
)

@Embeddable
data class GroupMemberId(
    val groupId: Long = 0,
    val userId: Long = 0
) : Serializable 