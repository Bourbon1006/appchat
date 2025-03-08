package org.example.appchathandler.service

import org.example.appchathandler.entity.Message
import org.example.appchathandler.entity.MessageType
import org.example.appchathandler.entity.Group
import org.example.appchathandler.repository.MessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import org.example.appchathandler.dto.MessageDTO
import org.example.appchathandler.dto.toDTO
import org.example.appchathandler.model.Message as ModelMessage
import org.example.appchathandler.model.MessageType as ModelMessageType
import org.example.appchathandler.repository.MessageReadStatusRepository
import org.example.appchathandler.repository.UserRepository
import org.example.appchathandler.dto.MessageSessionDTO
import org.example.appchathandler.entity.User
import org.example.appchathandler.dto.MessageSessionInfo
import org.example.appchathandler.repository.GroupRepository

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val userService: UserService,
    private val groupService: GroupService,
    private val messageReadStatusRepository: MessageReadStatusRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository
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
        val group = groupId?.let { groupService.getGroup(it).let { dto ->
            Group(
                id = dto.id,
                name = dto.name,
                creator = userService.getUser(dto.creator.id)
            )
        }}

        if (receiver == null && group == null) {
            throw IllegalArgumentException("必须指定接收者或群组")
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
            creator = userService.getUser(groupDto.creator.id)
        )
        return messageRepository.findByGroupOrderByTimestampAsc(group)
    }

    fun getPrivateMessages(userId: Long, otherId: Long): List<MessageDTO> {
        try {
            val user = userService.getUser(userId)
            val otherUser = userService.getUser(otherId)
            
            return messageRepository.findMessagesBetweenUsers(userId, otherId)
                .map { message -> 
                    MessageDTO(
                        id = message.id,
                        content = message.content,
                        timestamp = message.timestamp,
                        senderId = message.sender.id,
                        senderName = message.sender.username,
                        receiverId = message.receiver?.id ?: 0L,
                        receiverName = message.receiver?.username,
                        groupId = message.group?.id,
                        type = message.type,
                        fileUrl = message.fileUrl
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun getUserMessages(userId: Long): List<Message> {
        val user = userService.getUser(userId)
        return messageRepository.findByUserMessages(user)
    }

    fun getMessagesByDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Message> {
        val user = userService.getUser(userId)
        return messageRepository.findByDateRange(user, startDate, endDate)
    }

    fun searchMessages(userId: Long, keyword: String): List<Message> {
        val user = userService.getUser(userId)
        return messageRepository.searchMessages(user, "%$keyword%")
    }

    fun getPrivateMessagesByDateRange(
        userId1: Long,
        userId2: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Message> {
        return messageRepository.findByPrivateChatAndDateRange(userId1, userId2, startTime, endTime)
    }

    fun getGroupMessagesByDateRange(
        groupId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Message> {
        val group = groupService.getGroup(groupId)
        return messageRepository.findByGroupAndDateRange(
            Group(
                id = group.id,
                name = group.name,
                creator = userService.getUser(group.creator.id)
            ),
            startTime,
            endTime
        )
    }

    @Transactional
    fun deleteMessage(messageId: Long, userId: Long) {
        val message = messageRepository.findById(messageId).orElseThrow {
            IllegalArgumentException("Message with id $messageId not found")
        }

        // 确保 deletedForUsers 是可变的
        if (message.deletedForUsers.add(userId)) {
            messageRepository.save(message) // 只有在新增时才需要 save
        }
    }


    @Transactional
    fun deleteMessageCompletely(messageId: Long) {
        messageRepository.isMessageDeletedForAllUsers(messageId)
    }

    @Transactional
    fun deleteAllMessages(userId: Long, otherId: Long) {
        val messages = messageRepository.findMessagesBetweenUsers(userId, otherId)
        messages.forEach { message ->
            message.deletedForUsers.add(userId)
            messageRepository.save(message)
        }
    }

    fun getMessages(userId: Long, otherId: Long): List<MessageDTO> {
        return messageRepository.findMessagesBetweenUsers(userId, otherId)
            .map { message -> MessageDTO(
                id = message.id,
                content = message.content,
                timestamp = message.timestamp,
                senderId = message.sender.id,
                senderName = message.sender.username,
                receiverId = message.receiver?.id,
                receiverName = message.receiver?.username,
                groupId = message.group?.id,
                type = message.type,
                fileUrl = message.fileUrl
            )}
    }

    fun findById(messageId: Long): Message? {
        return messageRepository.findById(messageId).orElse(null)
    }

    fun markMessageAsDeleted(messageId: Long, userId: Long): Boolean {
        val message = findById(messageId) ?: return false
        
        // 如果是群聊消息，直接删除
        if (message.group != null) {
            messageRepository.deleteById(messageId)
            return true
        }

        // 如果是私聊消息，标记为该用户已删除
        val deletedUsers = message.deletedForUsers.toMutableSet()
        deletedUsers.add(userId)
        message.deletedForUsers = deletedUsers
        messageRepository.save(message)

        // 检查是否所有相关用户都已删除
        val allUsers = setOfNotNull(message.sender.id, message.receiver?.id)
        
        return if (allUsers.all { userId -> deletedUsers.contains(userId) }) {
            // 如果所有用户都已删除，从数据库中完全删除
            messageRepository.deleteById(messageId)
            true
        } else {
            false
        }
    }

    fun savePrivateMessage(message: Message, receiverId: Long): Message {
        try {
            println("⭐ Saving private message: senderId=${message.sender.id}, receiverId=$receiverId")
            val receiver = userService.getUser(receiverId)
            val newMessage = message.copy(receiver = receiver)
            val savedMessage = messageRepository.save(newMessage)
            println("✅ Private message saved successfully: id=${savedMessage.id}")
            return savedMessage
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error saving private message: ${e.message}")
            throw e
        }
    }

    fun saveGroupMessage(message: Message, groupId: Long): Message {
        try {
            println("⭐ Saving group message: senderId=${message.sender.id}, groupId=$groupId")
            val groupDto = groupService.getGroup(groupId)
            // 将 GroupDTO 转换为 Group 实体
            val group = Group(
                id = groupDto.id,
                name = groupDto.name,
                creator = userService.getUser(groupDto.creator.id),
                members = groupDto.members.map { userService.getUser(it.id) }.toMutableSet()
            )
            val newMessage = message.copy(group = group)
            val savedMessage = messageRepository.save(newMessage)
            println("✅ Group message saved successfully: id=${savedMessage.id}")
            return savedMessage
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error saving group message: ${e.message}")
            throw e
        }
    }

    fun markSessionAsRead(userId: Long, partnerId: Long, type: String) {
        val user = userRepository.findById(userId).orElseThrow()
        val unreadMessages = messageReadStatusRepository.findUnreadMessages(userId, partnerId)
        
        unreadMessages.forEach { message ->
            message.addReadStatus(user)
        }
        
        messageRepository.saveAll(unreadMessages)
    }

    fun getMessageSessions(userId: Long): List<MessageSessionInfo> {
        try {
            return messageRepository.findMessageSessions(userId)
        } catch (e: Exception) {
            e.printStackTrace() // 打印错误堆栈以便调试
            throw e
        }
    }

    fun markPrivateMessagesAsRead(userId: Long, partnerId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val partner = userRepository.findById(partnerId).orElseThrow { IllegalArgumentException("Partner not found") }

        // 获取所有未读消息并标记为已读
        messageRepository.findUnreadPrivateMessages(userId, partnerId).forEach { message ->
            message.markAsRead(user)
            messageRepository.save(message)
        }
    }

    fun markGroupMessagesAsRead(userId: Long, groupId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val group = groupRepository.findById(groupId).orElseThrow { IllegalArgumentException("Group not found") }

        // 获取所有未读的群消息并标记为已读
        messageRepository.findUnreadGroupMessages(userId, groupId).forEach { message ->
            message.markAsRead(user)
            messageRepository.save(message)
        }
    }
} 