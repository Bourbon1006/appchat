package org.example.appchathandler.service

import org.example.appchathandler.repository.FriendRequestRepository
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.RequestStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FriendRequestService(
    private val friendRequestRepository: FriendRequestRepository,
    private val userService: UserService
) {
    
    @Transactional
    fun sendFriendRequest(senderId: Long, receiverId: Long): FriendRequest {
        val sender = userService.getUser(senderId)
        val receiver = userService.getUser(receiverId)
        
        // 检查是否已经是好友
        if (sender.contacts.contains(receiver)) {
            throw IllegalStateException("已经是好友关系")
        }
        
        // 检查是否已经有待处理的请求
        val existingRequest = friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender, receiver, RequestStatus.PENDING
        )
        if (existingRequest != null) {
            throw IllegalStateException("已经发送过好友请求")
        }
        
        val request = FriendRequest(
            sender = sender,
            receiver = receiver
        )
        return friendRequestRepository.save(request)
    }
    
    @Transactional
    fun handleFriendRequest(requestId: Long, accept: Boolean): FriendRequest {
        val request = friendRequestRepository.findById(requestId).orElseThrow {
            RuntimeException("Friend request not found")
        }
        
        request.status = if (accept) RequestStatus.ACCEPTED else RequestStatus.REJECTED
        
        if (accept) {
            val sender = request.sender
            val receiver = request.receiver
            sender.contacts.add(receiver)
            receiver.contacts.add(sender)
        }
        
        return friendRequestRepository.save(request)
    }

    fun getPendingRequests(userId: Long): List<FriendRequest> {
        val user = userService.getUser(userId)
        return friendRequestRepository.findByReceiverAndStatus(user, RequestStatus.PENDING)
    }
} 