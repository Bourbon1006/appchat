package org.example.appchathandler.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.example.appchathandler.repository.FriendRepository
import org.example.appchathandler.service.MessageService
import org.example.appchathandler.entity.Friend
import org.example.appchathandler.entity.FriendId

@Service
class FriendService(
    private val friendRepository: FriendRepository,
    private val messageService: MessageService
) {
    @Transactional
    fun addFriend(userId: Long, friendId: Long) {
        // 添加双向好友关系
        friendRepository.save(Friend(userId = userId, contactId = friendId))
        friendRepository.save(Friend(userId = friendId, contactId = userId))
    }

    @Transactional
    fun deleteFriend(userId: Long, friendId: Long) {

        friendRepository.deleteReadStatusByUserIdAndPartnerId(userId, friendId)
        friendRepository.deleteReadStatusByUserIdAndPartnerId(friendId, userId)

        // 删除好友关系（双向）
        friendRepository.deleteByUserIdAndFriendId(userId, friendId)
        friendRepository.deleteByUserIdAndFriendId(friendId, userId)

        // 删除相关的聊天记录
        messageService.deletePrivateMessages(userId, friendId)

        // 删除相关的会话
        messageService.deletePrivateSession(userId, friendId)
    }

    fun getFriends(userId: Long): List<Friend> {
        return friendRepository.findByUserId(userId)
    }

    fun isFriend(userId: Long, friendId: Long): Boolean {
        return friendRepository.existsById(FriendId(userId, friendId))
    }
} 