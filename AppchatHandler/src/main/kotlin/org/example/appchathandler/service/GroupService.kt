package org.example.appchathandler.service

import org.example.appchathandler.dto.FileDTO
import org.example.appchathandler.dto.GroupCreateRequest
import org.example.appchathandler.dto.GroupDTO
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.entity.Group
import org.example.appchathandler.entity.GroupMember
import org.example.appchathandler.entity.GroupMemberId
import org.example.appchathandler.entity.User
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import org.example.appchathandler.repository.GroupRepository
import org.example.appchathandler.repository.GroupMemberRepository
import org.example.appchathandler.repository.UserRepository
import java.util.NoSuchElementException

interface GroupService {
    fun getGroupById(groupId: Long): GroupDTO
    fun createGroup(request: GroupCreateRequest): GroupDTO
    fun updateGroupName(groupId: Long, newName: String): GroupDTO
    fun updateGroupAvatar(groupId: Long, avatar: MultipartFile): GroupDTO
    fun getGroupMembers(groupId: Long): List<UserDTO>
    fun addMember(groupId: Long, userId: Long): GroupMember
    fun removeMember(groupId: Long, userId: Long)
    fun getGroupsByUserId(userId: Long): List<GroupDTO>
    fun updateGroupAvatar(groupId: Long, avatarUrl: String)
}

@Service
class GroupServiceImpl(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository, 
    private val groupMemberRepository: GroupMemberRepository,
    private val fileService: FileService
) : GroupService {

    override fun getGroupById(groupId: Long): GroupDTO {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found with id: $groupId") }
        return group.toDTO()
    }

    @Transactional
    override fun createGroup(request: GroupCreateRequest): GroupDTO {
        // 获取创建者
        val creator = userRepository.findById(request.creatorId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: ${request.creatorId}") }
        
        // 创建群组
        val group = Group(
            name = request.name,
            description = request.description,
            creator = creator,
            owner = creator // 默认创建者也是群主
        )
        
        val savedGroup = groupRepository.save(group)
        val now = LocalDateTime.now()

        // 添加创建者作为管理员
        val creatorMember = GroupMember(
            group = savedGroup,
            user = creator,
            joinedAt = now,
            isAdmin = true  // 群主设置为管理员
        )
        groupMemberRepository.save(creatorMember)
        
        // 添加其他成员
        request.memberIds?.forEach { memberId ->
            if (memberId != request.creatorId) {
                val user = userRepository.findById(memberId).orElse(null)
                user?.let {
                    val member = GroupMember(
                        group = savedGroup,
                        user = it,
                        joinedAt = now,  // 使用相同的创建时间
                        isAdmin = false   // 普通成员不是管理员
                    )
                    groupMemberRepository.save(member)
                }
            }
        }
        
        return savedGroup.toDTO()
    }

    @Transactional
    override fun updateGroupName(groupId: Long, newName: String): GroupDTO {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found with id: $groupId") }
        
        group.name = newName
        
        val updatedGroup = groupRepository.save(group)
        return updatedGroup.toDTO()
    }

    @Transactional
    override fun updateGroupAvatar(groupId: Long, avatar: MultipartFile): GroupDTO {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found with id: $groupId") }
        
        // 保存头像文件并获取URL
        val fileDTO = fileService.saveFile(avatar)
        
        group.avatarUrl = fileDTO.url
        
        val updatedGroup = groupRepository.save(group)
        return updatedGroup.toDTO()
    }

    override fun getGroupMembers(groupId: Long): List<UserDTO> {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found with id: $groupId") }
        
        return group.members.map { member ->
            member.toDTO()
        }
    }

    @Transactional
    override fun addMember(groupId: Long, userId: Long): GroupMember {
        val group = groupRepository.findById(groupId)
            .orElseThrow { RuntimeException("Group not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { RuntimeException("User not found") }
            
        val groupMember = GroupMember(
            id = GroupMemberId(groupId, userId),
            group = group,
            user = user,
            joinedAt = LocalDateTime.now()
        )
        
        return groupMemberRepository.save(groupMember)
    }

    @Transactional
    override fun removeMember(groupId: Long, userId: Long) {
        val group = groupRepository.findById(groupId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found with id: $groupId") }
        
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId") }
        
        // 移除成员
        if (group.members.contains(user)) {
            group.members.remove(user)
            
            // 如果是最后一个成员，删除群组
            if (group.members.isEmpty()) {
                groupRepository.delete(group)
            } else {
                // 如果移除的是群主，需要选择新群主
                if (group.owner.id == userId) {
                    val newOwner = group.members.first()
                    group.owner = newOwner
                }
                groupRepository.save(group)
            }
        }
    }

    override fun getGroupsByUserId(userId: Long): List<GroupDTO> {
        // 获取用户所属的所有群组
        val groups = groupRepository.findByMembersId(userId)
        
        // 转换为DTO并返回
        return groups.map { it.toDTO() }
    }

    override fun updateGroupAvatar(groupId: Long, avatarUrl: String) {
        val group = groupRepository.findById(groupId).orElseThrow {
            throw NoSuchElementException("群组不存在")
        }
        group.avatarUrl = avatarUrl
        groupRepository.save(group)
    }
} 