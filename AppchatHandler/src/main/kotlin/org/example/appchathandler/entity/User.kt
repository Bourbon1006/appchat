package org.example.appchathandler.entity

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(unique = true)
    val username: String,
    
    val password: String,
    
    @Column(nullable = true)
    var nickname: String? = null,
    
    @Column(nullable = true)
    var avatar: String? = null,
    
    @Column(name = "is_online")
    var online: Boolean = false,

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "user_contacts",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "contact_id")]
    )
    var contacts: MutableSet<User> = mutableSetOf()
) {
    constructor() : this(0, "", "", null, null, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as User
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
} 