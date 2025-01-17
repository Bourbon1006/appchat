package org.example.appchathandler.entity

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    
    @Column(unique = true)
    var username: String,
    
    @JsonIgnore
    @Column(length = 60)
    var password: String,
    
    var nickname: String? = null,
    
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
    var contacts: MutableSet<User> = mutableSetOf(),

    @ManyToMany(mappedBy = "members")
    var groups: MutableSet<Group> = mutableSetOf()
) {
    constructor() : this(
        username = "",
        password = ""
    )
}

@Converter(autoApply = true)
class BooleanToIntConverter : AttributeConverter<Boolean, Int> {
    override fun convertToDatabaseColumn(attribute: Boolean?): Int {
        return if (attribute == true) 1 else 0
    }

    override fun convertToEntityAttribute(dbData: Int?): Boolean {
        return dbData == 1
    }
}