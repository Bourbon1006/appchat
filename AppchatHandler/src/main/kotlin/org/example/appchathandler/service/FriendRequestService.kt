package org.example.appchathandler.service

import org.example.appchathandler.repository.FriendRequestRepository
import org.example.appchathandler.entity.FriendRequest
import org.example.appchathandler.entity.RequestStatus
import org.example.appchathandler.event.FriendRequestEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FriendRequestService(
    private val friendRequestRepository: FriendRequestRepository,
    private val friendService: FriendService,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher
) {
    
    @Transactional
    fun sendFriendRequest(senderId: Long, receiverId: Long): FriendRequest {
        val sender = userService.getUser(senderId)
        val receiver = userService.getUser(receiverId)
        
        // 检查是否已经是好友
        if (friendService.isFriend(sender.id, receiver.id)) {
            throw IllegalStateException("已经是好友关系")
        }
        
        // 检查是否已经有待处理的请求
        val existingRequest = friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender = sender,
            receiver = receiver,
            status = RequestStatus.PENDING
        )
        if (existingRequest != null) {
            throw IllegalStateException("已经发送过好友请求")
        }
        
        val request = FriendRequest(
            sender = sender,
            receiver = receiver
        )
        val savedRequest = friendRequestRepository.save(request)
        
        // 添加日志
        println("🔔 Publishing FriendRequestEvent for request ${savedRequest.id} from ${savedRequest.sender.username} to ${savedRequest.receiver.username}")
        
        // 发布好友请求事件
        eventPublisher.publishEvent(FriendRequestEvent(this, savedRequest))
        
        return savedRequest
    }
    
    @Transactional
    fun handleFriendRequest(requestId: Long, accept: Boolean): FriendRequest {
        val request = friendRequestRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("好友请求不存在: $requestId") }
        
        // 检查请求状态
        if (request.status != RequestStatus.PENDING) {
            throw IllegalStateException("该请求已被处理: ${request.status}")
        }
        
        println("🔄 处理好友请求: requestId=$requestId, accept=$accept, 当前状态=${request.status}")
        
        request.status = if (accept) RequestStatus.ACCEPTED else RequestStatus.REJECTED
        
        if (accept) {
            try {
                // 添加好友关系
                friendService.addFriend(request.sender.id, request.receiver.id)
                println("✅ 已添加好友关系: ${request.sender.username} <-> ${request.receiver.username}")
            } catch (e: Exception) {
                println("❌ 添加好友关系失败: ${e.message}")
                throw e
            }
        }
        
        return friendRequestRepository.save(request)
    }

    fun getPendingRequests(userId: Long): List<FriendRequest> {
        val user = userService.getUser(userId)
        return friendRequestRepository.findByReceiverAndStatus(user, RequestStatus.PENDING)
    }

    fun save(request: FriendRequest): FriendRequest {
        // 检查是否已经是好友
        if (friendService.isFriend(request.sender.id, request.receiver.id)) {
            throw RuntimeException("已经是好友关系")
        }

        // 检查是否已经有未处理的请求
        val existingRequest = friendRequestRepository.findBySenderAndReceiverAndStatus(
            sender = request.sender,
            receiver = request.receiver,
            status = RequestStatus.PENDING
        )
        if (existingRequest != null) {
            throw RuntimeException("已经发送过好友请求")
        }

        // 保存请求
        return friendRequestRepository.save(request)
    }

    fun getFriendRequest(requestId: Long): FriendRequest? {
        return friendRequestRepository.findById(requestId).orElse(null)
    }
} 