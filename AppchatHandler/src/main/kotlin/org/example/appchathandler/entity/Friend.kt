package org.example.appchathandler.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_contacts")
@IdClass(FriendId::class)
data class Friend(
    @Id
    @Column(name = "contact_id")
    val contactId: Long,

    @Id
    @Column(name = "user_id")
    val userId: Long
)

data class FriendId(
    val contactId: Long = 0,
    val userId: Long = 0
) : java.io.Serializable 