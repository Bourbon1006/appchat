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
    
    var password: String,
    
    var nickname: String? = null,
    
    var avatar: String? = null,
    
    @Column(name = "is_online")
    @Convert(converter = BooleanToIntConverter::class)
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

    override fun toString(): String {
        return "User(id=$id, username='$username', online=$online)"
    }

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

@Converter(autoApply = true)  // 添加 autoApply = true
class BooleanToIntConverter : AttributeConverter<Boolean, Int> {
    override fun convertToDatabaseColumn(attribute: Boolean?): Int {
        println("Converting to DB: $attribute -> ${if (attribute == true) 1 else 0}")  // 添加日志
        return if (attribute == true) 1 else 0
    }

    override fun convertToEntityAttribute(dbData: Int?): Boolean {
        println("Converting from DB: $dbData -> ${dbData == 1}")  // 添加日志
        return dbData == 1
    }
}