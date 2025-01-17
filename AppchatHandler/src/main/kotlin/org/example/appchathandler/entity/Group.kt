package org.example.appchathandler.entity

import jakarta.persistence.*

@Entity
@Table(name = "`groups`")
class Group(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    var name: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creator_id")
    var creator: User,

    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "group_members",
        joinColumns = [JoinColumn(name = "group_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var members: MutableSet<User> = mutableSetOf()
) {
    constructor() : this(name = "", creator = User())
} 