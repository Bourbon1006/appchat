package org.example.appchathandler.repository

import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.RequestStatus
import org.example.appchathandler.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FriendRequestRepository : JpaRepository<FriendRequest, Long> {
    fun findBySenderAndReceiverAndStatus(
        sender: User,
        receiver: User,
        status: RequestStatus
    ): FriendRequest?

    fun findByReceiverAndStatus(receiver: User, status: RequestStatus): List<FriendRequest>
} 