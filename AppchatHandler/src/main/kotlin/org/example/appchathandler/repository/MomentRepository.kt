package org.example.appchathandler.repository

import org.example.appchathandler.entity.Moment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MomentRepository : JpaRepository<Moment, Long> {
    @Query("SELECT m FROM Moment m WHERE m.user.id IN :userIds ORDER BY m.createTime DESC")
    fun findByUserIdInOrderByCreateTimeDesc(userIds: List<Long>): List<Moment>
    
    fun findByUserIdOrderByCreateTimeDesc(userId: Long): List<Moment>
} 