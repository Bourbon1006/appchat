package org.example.appchathandler.repository

import org.example.appchathandler.entity.FriendGroup
import org.springframework.data.jpa.repository.JpaRepository

interface FriendGroupRepository : JpaRepository<FriendGroup, Long> {
    fun findByUserId(userId: Long): List<FriendGroup>
    fun findByUserIdAndName(userId: Long, name: String): FriendGroup?
}