package org.example.appchathandler.service

import org.example.appchathandler.entity.FriendGroup
import org.example.appchathandler.entity.User
import org.example.appchathandler.repository.FriendGroupRepository
import org.example.appchathandler.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FriendGroupService(
    private val friendGroupRepository: FriendGroupRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun createFriendGroup(userId: Long, name: String): FriendGroup {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        
        // 检查是否已存在同名分组
        if (friendGroupRepository.findByUserIdAndName(userId, name) != null) {
            throw IllegalStateException("分组名称已存在")
        }
        
        val friendGroup = FriendGroup(
            name = name,
            user = user
        )
        return friendGroupRepository.save(friendGroup)
    }

    @Transactional
    fun deleteFriendGroup(groupId: Long, userId: Long) {
        val friendGroup = getFriendGroupWithAccess(groupId, userId)
        friendGroupRepository.delete(friendGroup)
    }

    @Transactional
    fun updateFriendGroupName(groupId: Long, userId: Long, newName: String): FriendGroup {
        val friendGroup = getFriendGroupWithAccess(groupId, userId)
        
        // 检查新名称是否已存在
        if (friendGroupRepository.findByUserIdAndName(userId, newName) != null) {
            throw IllegalStateException("分组名称已存在")
        }
        
        friendGroup.name = newName
        return friendGroupRepository.save(friendGroup)
    }

    @Transactional
    fun addFriendToGroup(groupId: Long, userId: Long, friendId: Long): FriendGroup {
        val friendGroup = getFriendGroupWithAccess(groupId, userId)
        val friend = userRepository.findById(friendId).orElseThrow { IllegalArgumentException("好友不存在") }
        
        friendGroup.members.add(friend)
        return friendGroupRepository.save(friendGroup)
    }

    @Transactional
    fun removeFriendFromGroup(groupId: Long, userId: Long, friendId: Long): FriendGroup {
        val friendGroup = getFriendGroupWithAccess(groupId, userId)
        val friend = userRepository.findById(friendId).orElseThrow { IllegalArgumentException("好友不存在") }
        
        friendGroup.members.remove(friend)
        return friendGroupRepository.save(friendGroup)
    }

    fun getFriendGroups(userId: Long): List<FriendGroup> {
        return friendGroupRepository.findByUserId(userId)
    }

    fun getFriendGroupMembers(groupId: Long, userId: Long): Set<User> {
        val friendGroup = getFriendGroupWithAccess(groupId, userId)
        return friendGroup.members
    }

    private fun getFriendGroupWithAccess(groupId: Long, userId: Long): FriendGroup {
        val friendGroup = friendGroupRepository.findById(groupId)
            .orElseThrow { IllegalArgumentException("好友分组不存在") }
        
        if (friendGroup.user.id != userId) {
            throw IllegalAccessException("无权访问该好友分组")
        }
        
        return friendGroup
    }
}