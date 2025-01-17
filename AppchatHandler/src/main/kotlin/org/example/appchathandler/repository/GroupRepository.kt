package org.example.appchathandler.repository

import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupRepository : JpaRepository<Group, Long> {
    fun findByMembersContaining(user: User): List<Group>
} 