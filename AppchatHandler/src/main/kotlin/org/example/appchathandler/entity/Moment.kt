package org.example.appchathandler.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "moments")
data class Moment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val content: String,
    
    @Column(name = "image_url")
    val imageUrl: String? = null,
    
    @Column(name = "create_time")
    val createTime: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "like_count")
    var likeCount: Int = 0,
    
    @Column(name = "comment_count")
    var commentCount: Int = 0
) 