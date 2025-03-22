package org.example.appchathandler.repository

import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.GroupMember
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GroupRepository : JpaRepository<Group, Long> {
    fun findByMembersContaining(user: User): List<Group>

    @Query("SELECT COUNT(m) > 0 FROM Group g JOIN g.members m WHERE g.id = :groupId AND m.id = :userId")
    fun existsByGroupIdAndMemberId(groupId: Long, userId: Long): Boolean

    fun findByMembersId(userId: Long): List<Group>
}

@Repository
interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    fun findByGroupId(groupId: Long): List<GroupMember>
    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMember?
    fun countByGroupId(groupId: Long): Long
} 