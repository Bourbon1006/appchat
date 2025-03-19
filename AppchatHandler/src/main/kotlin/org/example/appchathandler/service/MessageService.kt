package org.example.appchathandler.service

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
import org.example.appchathandler.dto.MessageSessionInfo
import org.example.appchathandler.entity.*
import org.example.appchathandler.repository.GroupRepository
import org.springframework.beans.factory.annotation.Autowired
import org.example.appchathandler.service.EventService
import org.example.appchathandler.websocket.ChatWebSocketHandler
import org.springframework.context.annotation.Lazy

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val eventService: EventService,
    private val userService: UserService,
    private val groupService: GroupService,
    private val messageReadStatusRepository: MessageReadStatusRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    @Lazy private val webSocketHandler: ChatWebSocketHandler
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
        try {
            // First, delete all read status entries for this message
            messageReadStatusRepository.deleteByMessageId(messageId)
            
            // Then delete the message itself
            messageRepository.deleteById(messageId)
            
            println("✅ Message $messageId completely deleted")
        } catch (e: Exception) {
            println("❌ Error deleting message completely: ${e.message}")
            e.printStackTrace()
            throw e
        }
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

    @Transactional
    fun markMessageAsDeleted(messageId: Long, userId: Long): Boolean {
        return try {
            val message = messageRepository.findById(messageId).orElse(null) 
                ?: return false

            // For group messages, mark as deleted for this user
            if (message.group != null) {
                val deletedUsers = HashSet(message.deletedForUsers)
                deletedUsers.add(userId)
                message.deletedForUsers = deletedUsers
                messageRepository.save(message)
                return true
            }

            // For private messages
            val deletedUsers = HashSet(message.deletedForUsers)
            deletedUsers.add(userId)
            message.deletedForUsers = deletedUsers
            messageRepository.save(message)

            // Check if all relevant users have deleted the message
            val allRelevantUsers = setOfNotNull(
                message.sender.id,
                message.receiver?.id
            )

            allRelevantUsers.all { deletedUsers.contains(it) }
        } catch (e: Exception) {
            println("❌ Error marking message as deleted: ${e.message}")
            e.printStackTrace()
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

    fun markGroupMessagesAsRead(userId: Long, groupId: Long) {
        val user = userRepository.findById(userId).orElseThrow()
        val unreadMessages = messageRepository.findUnreadGroupMessages(userId, groupId)
        
        unreadMessages.forEach { message ->
            message.addReadStatus(user)
        }
        
        messageRepository.saveAll(unreadMessages)
    }

    @Transactional
    fun markPrivateMessagesAsRead(userId: Long, partnerId: Long) {
        println("⭐ Marking private messages as read: userId=$userId, partnerId=$partnerId")
        
        // 获取所有未读消息
        val unreadMessages = messageReadStatusRepository.findUnreadMessages(userId, partnerId)

        // 为每条消息创建已读状态
        unreadMessages.forEach { message ->
            if (!messageReadStatusRepository.existsByMessageIdAndUserId(message.id, userId)) {
                val readStatus = MessageReadStatus(
                    message = message,
                    userId = userId
                )
                messageReadStatusRepository.save(readStatus)
                println("✅ Marked message ${message.id} as read for user $userId")
            }
        }

        println("✅ Successfully marked all private messages as read")
    }

    @Transactional
    fun deletePrivateMessages(userId: Long, friendId: Long) {
        messageRepository.deleteByPrivateChat(userId, friendId)
    }

    @Transactional
    fun deletePrivateSession(userId: Long, partnerId: Long) {
        // 标记所有消息为已删除
        val messages = messageRepository.findMessagesBetweenUsers(userId, partnerId)
        messages.forEach { message ->
            message.deletedForUsers.add(userId)
            messageRepository.save(message)
        }
    }

    fun getMessageSessions(userId: Long): List<MessageSessionDTO> {
        val privateMessages = messageRepository.findLatestPrivateMessagesByUser(userId)
        val groupMessages = messageRepository.findLatestGroupMessagesByUser(userId)
        val sessions = mutableListOf<MessageSessionDTO>()
        
        // Handle private chat sessions
        privateMessages.forEach { message ->
            val partnerId = if (message.sender.id == userId) message.receiver?.id else message.sender.id
            val partner = userRepository.findById(partnerId ?: 0).orElse(null)
            
            partner?.let { user ->
                sessions.add(MessageSessionDTO(
                    id = message.id,
                    partnerId = partnerId ?: 0,
                    partnerName = user.username,
                    lastMessage = message.content,
                    lastMessageTime = message.timestamp,
                    unreadCount = messageReadStatusRepository.countUnreadMessages(userId, partnerId ?: 0),
                    type = MessageSessionInfo.Type.PRIVATE,
                    partnerAvatar = user.avatarUrl
                ))
            }
        }
        
        // Handle group chat sessions
        groupMessages.forEach { message ->
            message.group?.let { group ->
                sessions.add(MessageSessionDTO(
                    id = message.id,
                    partnerId = group.id,
                    partnerName = group.name,
                    lastMessage = message.content,
                    lastMessageTime = message.timestamp,
                    unreadCount = messageReadStatusRepository.countUnreadMessages(userId, group.id),
                    type = MessageSessionInfo.Type.GROUP,
                    partnerAvatar = group.avatarUrl
                ))
            }
        }
        
        return sessions.sortedByDescending { it.lastMessageTime }
    }

    fun canUserDeleteMessage(userId: Long, message: Message): Boolean {
        return try {
            // User can delete if they are the sender
            if (message.sender.id == userId) {
                return true
            }

            // For private messages, receiver can also delete
            if (message.receiver != null && message.group == null && message.receiver!!.id == userId) {
                return true
            }

            // For group messages, check if user is a member of the group
            if (message.group != null) {
                return groupRepository.existsByGroupIdAndMemberId(message.group!!.id, userId)
            }

            false
        } catch (e: Exception) {
            println("❌ Error checking message delete permission: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}