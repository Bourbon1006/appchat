package org.example.appchathandler.entity

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "`groups`")
data class Group(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    var name: String,
    
    var description: String? = null,
    
    @Column(name = "avatar_url")
    var avatarUrl: String? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    val creator: User,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    var owner: User,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ManyToMany
    @JoinTable(
        name = "group_members",
        joinColumns = [JoinColumn(name = "group_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    val members: MutableSet<User> = mutableSetOf()
) 