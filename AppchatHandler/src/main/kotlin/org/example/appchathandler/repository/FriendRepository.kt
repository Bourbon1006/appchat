package org.example.appchathandler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.example.appchathandler.entity.Friend
import org.example.appchathandler.entity.FriendId
import org.springframework.stereotype.Repository

@Repository
interface FriendRepository : JpaRepository<Friend, FriendId> {
    // ... 其他方法 ...

    @Modifying
    @Query("DELETE FROM Friend f WHERE (f.userId = :userId AND f.contactId = :friendId)")
    fun deleteByUserIdAndFriendId(userId: Long, friendId: Long)

    @Query("SELECT f FROM Friend f WHERE f.userId = :userId")
    fun findByUserId(userId: Long): List<Friend>

    @Modifying
    @Query("""
        DELETE FROM MessageReadStatus mrs 
        WHERE mrs.message.id IN (
            SELECT m.id FROM Message m 
            WHERE m.sender.id = :userId 
            AND m.receiver.id = :partnerId
        )
    """)
    fun deleteReadStatusByUserIdAndPartnerId(userId: Long, partnerId: Long)

} 