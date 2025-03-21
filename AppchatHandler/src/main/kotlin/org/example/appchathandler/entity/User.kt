package org.example.appchathandler.entity

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(unique = true, nullable = false)
    var username: String,
    
    @Column(nullable = false)
    var password: String,
    
    @Column(name = "avatar_url")
    var avatarUrl: String? = null,
    
    @Column(name = "nickname")
    var nickname: String? = null,
    
    @Column(name = "online_status")
    var onlineStatus: Int = 0,  // 0-离线, 1-在线, 2-忙碌
    
    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "user_contacts",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "contact_id")]
    )
    var contacts: MutableSet<User> = mutableSetOf()
) {
    constructor() : this(
        username = "",
        password = ""
    )
    
    // 重写 equals 方法，只比较 id
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }
    
    // 重写 hashCode 方法，只使用 id
    override fun hashCode(): Int {
        return id.hashCode()
    }
}