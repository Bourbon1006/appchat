package org.example.appchathandler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.example.appchathandler.entity.UserContact
import org.springframework.stereotype.Repository

@Repository
interface UserContactRepository : JpaRepository<UserContact, Long> {
    @Modifying
    @Query("DELETE FROM UserContact uc WHERE (uc.userId = :userId AND uc.contactId = :friendId)")
    fun deleteByUserIdAndContactId(userId: Long, friendId: Long)
} 