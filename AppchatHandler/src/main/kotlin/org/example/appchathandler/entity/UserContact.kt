package org.example.appchathandler.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_contacts")
data class UserContact(
    @Id
    @Column(name = "contact_id")
    val contactId: Long,

    @Column(name = "user_id")
    val userId: Long
) 