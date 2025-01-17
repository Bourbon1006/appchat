package org.example.appchathandler.service

import org.example.appchathandler.dto.GroupDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.entity.Group
import org.example.appchathandler.repository.GroupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GroupService(
    private val groupRepository: GroupRepository,
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(GroupService::class.java)

    fun createGroup(name: String, creatorId: Long, memberIds: List<Long>): GroupDTO {
        try {
            logger.info("Creating group: name=$name, creatorId=$creatorId, memberIds=$memberIds")
            
            // 获取创建者
            val creator = userService.getUser(creatorId)
            
            // 获取成员列表，并确保包含创建者
            val members = memberIds.map { userService.getUser(it) }.toMutableSet()
            members.add(creator)

            // 创建群组
            val group = Group(
                name = name,
                creator = creator,
                members = members
            )

            // 保存群组
            val savedGroup = groupRepository.save(group)
            logger.info("Group created successfully: ${savedGroup.id}, members: ${savedGroup.members.map { it.id }}")
            
            return savedGroup.toDTO()
        } catch (e: Exception) {
            logger.error("Error creating group", e)
            throw e
        }
    }

    fun getGroup(groupId: Long): GroupDTO {
        return groupRepository.findById(groupId)
            .orElseThrow { NoSuchElementException("群组不存在") }
            .toDTO()
    }

    @Transactional
    fun addMember(groupId: Long, userId: Long): GroupDTO {
        val group = groupRepository.findById(groupId)
            .orElseThrow { NoSuchElementException("群组不存在") }
        val user = userService.getUser(userId)
        group.members.add(user)
        return groupRepository.save(group).toDTO()
    }

    @Transactional
    fun removeMember(groupId: Long, userId: Long): GroupDTO {
        val group = groupRepository.findById(groupId)
            .orElseThrow { NoSuchElementException("群组不存在") }
        val user = userService.getUser(userId)
        group.members.remove(user)
        return groupRepository.save(group).toDTO()
    }

    fun getUserGroups(userId: Long): List<GroupDTO> {
        val user = userService.getUser(userId)
        return groupRepository.findByMembersContaining(user).map { it.toDTO() }
    }
} 