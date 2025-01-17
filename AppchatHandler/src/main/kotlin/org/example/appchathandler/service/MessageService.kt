package org.example.appchathandler.service

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import org.example.appchathandler.entity.Group
import org.example.appchathandler.repository.MessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val userService: UserService,
    private val groupService: GroupService
) {
    fun createMessage(
        content: String,
        senderId: Long,
        receiverId: Long? = null,
        groupId: Long? = null,
        type: MessageType = MessageType.TEXT,
        fileUrl: String? = null
    ): Message {
        val sender = userService.getUser(senderId)
        val receiver = receiverId?.let { userService.getUser(it) }
        val group = groupId?.let { groupId ->
            val groupDto = groupService.getGroup(groupId)
            Group(
                id = groupDto.id,
                name = groupDto.name,
                creator = userService.getUser(groupDto.creator.id),
                members = groupDto.members.map { userService.getUser(it.id) }.toMutableSet()
            )
        }

        val message = Message(
            content = content,
            sender = sender,
            receiver = receiver,
            group = group,
            type = type,
            fileUrl = fileUrl
        )

        return messageRepository.save(message)
    }

    fun getGroupMessages(groupId: Long): List<Message> {
        val groupDto = groupService.getGroup(groupId)
        val group = Group(
            id = groupDto.id,
            name = groupDto.name,
            creator = userService.getUser(groupDto.creator.id),
            members = groupDto.members.map { userService.getUser(it.id) }.toMutableSet()
        )
        return messageRepository.findByGroupOrderByTimestampAsc(group)
    }

    fun getPrivateMessages(userId1: Long, userId2: Long): List<Message> {
        val user1 = userService.getUser(userId1)
        val user2 = userService.getUser(userId2)
        return messageRepository.findByPrivateChat(user1, user2)
    }
} 