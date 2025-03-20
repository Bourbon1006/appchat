package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "moment_likes")
data class MomentLike(
    @EmbeddedId
    val id: MomentLikeId,
    
    @Column(name = "create_time")
    val createTime: LocalDateTime = LocalDateTime.now()
)

@Embeddable
data class MomentLikeId(
    @Column(name = "moment_id")
    val momentId: Long,
    
    @Column(name = "user_id")
    val userId: Long
) : java.io.Serializable 