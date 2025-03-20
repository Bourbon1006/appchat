package org.example.appchathandler.repository

import org.example.appchathandler.entity.MomentLike
import org.example.appchathandler.entity.MomentLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MomentLikeRepository : JpaRepository<MomentLike, MomentLikeId> {
    @Query("DELETE FROM MomentLike ml WHERE ml.id.momentId = :momentId")
    @Modifying
    fun deleteByMomentId(@Param("momentId") momentId: Long)

    @Query("SELECT ml FROM MomentLike ml WHERE ml.id.momentId = :momentId")
    fun findByMomentId(@Param("momentId") momentId: Long): List<MomentLike>
} 