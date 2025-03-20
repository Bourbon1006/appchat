package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "moment_comments")
data class MomentComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moment_id")
    val moment: Moment,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val content: String,
    
    @Column(name = "create_time")
    val createTime: LocalDateTime = LocalDateTime.now()
) 