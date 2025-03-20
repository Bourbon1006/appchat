package org.example.appchathandler.repository

import org.example.appchathandler.entity.MomentComment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MomentCommentRepository : JpaRepository<MomentComment, Long> {
    fun findByMomentIdOrderByCreateTimeAsc(momentId: Long): List<MomentComment>
    
    @Query("DELETE FROM MomentComment mc WHERE mc.moment.id = :momentId")
    @Modifying
    fun deleteByMomentId(@Param("momentId") momentId: Long)
} 