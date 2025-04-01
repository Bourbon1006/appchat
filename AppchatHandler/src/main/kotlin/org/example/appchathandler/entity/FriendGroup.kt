package org.example.appchathandler.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*

@Entity
@Table(name = "friend_groups")
@JsonIgnoreProperties(
    "hibernateLazyInitializer", 
    "handler",
    "fieldHandler"
)
data class FriendGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    val user: User,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "friend_group_members",
        joinColumns = [JoinColumn(name = "group_id")],
        inverseJoinColumns = [JoinColumn(name = "contact_id")]
    )
    val members: MutableSet<User> = mutableSetOf()
)