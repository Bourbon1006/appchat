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
import org.springframework.context.ApplicationEventPublisher
import org.example.appchathandler.event.SessionsUpdateEvent

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val messageReadStatusRepository: MessageReadStatusRepository
) {
    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    fun createMessage(
        content: String,
        senderId: Long,
        receiverId: Long? = null,
        groupId: Long? = null,
        type: MessageType = MessageType.TEXT,
        fileUrl: String? = null
    ): Message {
        val sender = userRepository.findById(senderId).orElseThrow {
            IllegalArgumentException("User with id $senderId not found")
        }
        val receiver = receiverId?.let { userRepository.findById(it).orElse(null) }
        val group = groupId?.let { groupRepository.findById(it).orElse(null) }

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

    fun getGroupMessages(groupId: Long, userId: Long): List<MessageDTO> {
        return messageRepository.findGroupMessages(groupId, userId)
            .map { message ->
                MessageDTO(
                    id = message.id,
                    content = message.content,
                    timestamp = message.timestamp,
                    senderId = message.sender.id,
                    senderName = message.sender.username,
                    receiverId = null,
                    receiverName = null,
                    groupId = message.group?.id,
                    type = message.type,
                    fileUrl = message.fileUrl
                )
            }
    }

    fun getPrivateMessages(userId: Long, otherId: Long): List<MessageDTO> {
        try {
            println("📨 Getting private messages between users $userId and $otherId")
            val messages = messageRepository.findMessagesBetweenUsers(userId, otherId)
            println("✅ Found ${messages.size} messages")
            return messages.map { it.toDTO() }
        } catch (e: Exception) {
            println("❌ Error getting private messages: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun getUserMessages(userId: Long): List<Message> {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User with id $userId not found")
        }
        return messageRepository.findByUserMessages(user)
    }

    fun getMessagesByDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Message> {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User with id $userId not found")
        }
        return messageRepository.findByDateRange(user, startDate, endDate)
    }

    fun searchMessages(userId: Long, keyword: String): List<Message> {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User with id $userId not found")
        }
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
        val group = groupRepository.findById(groupId).orElseThrow {
            IllegalArgumentException("Group with id $groupId not found")
        }
        return messageRepository.findByGroupAndDateRange(
            Group(
                id = group.id,
                name = group.name,
                creator = userRepository.findById(group.creator.id).orElse(null)
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
            messageRepository.deleteAllReadStatusesForMessage(messageId)
            
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

            // 将当前消息标记为该用户已删除
            val deletedUsers = HashSet(message.deletedForUsers)
            deletedUsers.add(userId)
            message.deletedForUsers = deletedUsers
            messageRepository.save(message)

            // 如果删除的是最后一条消息，更新会话的最后一条消息
            val otherId = if (message.group != null) {
                message.group!!.id
            } else {
                if (message.sender.id == userId) message.receiver?.id ?: 0L
                else message.sender.id
            }
            
            updateLastMessageIfNeeded(message, userId, otherId)

            false
        } catch (e: Exception) {
            println("❌ Error marking message as deleted: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun updateLastMessageIfNeeded(deletedMessage: Message, userId: Long, otherId: Long) {
        try {
            // 获取会话的最后一条消息
            val lastMessage = if (deletedMessage.group != null) {
                // 群聊消息
                messageRepository.findLastGroupMessage(
                    deletedMessage.group!!.id,
                    userId
                )
            } else {
                // 私聊消息
                messageRepository.findLastPrivateMessage(
                    userId,
                    otherId
                )
            }

            // 如果删除的是最后一条消息，更新会话状态
            if (lastMessage != null && lastMessage.id == deletedMessage.id) {
                // 获取倒数第二条消息作为新的最后一条消息
                val newLastMessage = if (deletedMessage.group != null) {
                    messageRepository.findSecondLastGroupMessage(
                        deletedMessage.group!!.id,
                        userId
                    )
                } else {
                    messageRepository.findSecondLastPrivateMessage(
                        userId,
                        otherId
                    )
                }
                
                // 更新会话的最后一条消息
                if (newLastMessage != null) {
                    // 触发会话更新事件
                    notifySessionUpdate(userId)
                }
            }
        } catch (e: Exception) {
            println("❌ Error updating last message: ${e.message}")
            e.printStackTrace()
        }
    }

    fun savePrivateMessage(message: Message, receiverId: Long): Message {
        try {
            println("⭐ Saving private message: senderId=${message.sender.id}, receiverId=$receiverId")
            val receiver = userRepository.findById(receiverId).orElseThrow {
                IllegalArgumentException("User with id $receiverId not found")
            }
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
            val group = groupRepository.findById(groupId).orElseThrow {
                IllegalArgumentException("Group with id $groupId not found")
            }
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
        val unreadMessages = messageRepository.findUnreadMessages(userId, partnerId)
        
        unreadMessages.forEach { message ->
            message.addReadStatus(user)
        }
        
        messageRepository.saveAll(unreadMessages)
    }

    @Transactional
    fun markGroupMessagesAsRead(userId: Long, groupId: Long) {
        try {
            println("📬 Marking group messages as read: userId=$userId, groupId=$groupId")
            
            // 查找所有群组中的未读消息
            val unreadMessages = messageRepository.findUnreadGroupMessages(userId, groupId)
            println("📊 Found ${unreadMessages.size} unread group messages")
            
            // 标记为已读
            for (message in unreadMessages) {
                // 检查消息是否已经被标记为已读
                val isAlreadyRead = messageReadStatusRepository.existsByMessageIdAndUserId(message.id, userId)
                
                if (!isAlreadyRead) {
                    // 创建新的 MessageReadStatus 对象
                    val readStatus = MessageReadStatus(
                        message = message,
                        userId = userId,
                        readTime = LocalDateTime.now()
                    )
                    messageReadStatusRepository.save(readStatus)
                    println("✅ Marked group message ${message.id} as read")
                }
            }
            
            // 通知会话更新
            notifySessionUpdate(userId)
            
        } catch (e: Exception) {
            println("❌ Error marking group messages as read: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Transactional
    fun markPrivateMessagesAsRead(userId: Long, partnerId: Long) {
        try {
            println("📬 Marking private messages as read: userId=$userId, partnerId=$partnerId")
            
            // 查找所有从 partnerId 发送给 userId 的未读消息
            val unreadMessages = messageRepository.findUnreadMessagesFromUser(partnerId, userId)
            println("📊 Found ${unreadMessages.size} unread messages")
            
            // 标记为已读
            for (message in unreadMessages) {
                // 检查消息是否已经被标记为已读
                val isAlreadyRead = messageReadStatusRepository.existsByMessageIdAndUserId(message.id, userId)
                
                if (!isAlreadyRead) {
                    // 创建新的 MessageReadStatus 对象，但不要设置双向关联
                    val readStatus = MessageReadStatus(
                        message = message,
                        userId = userId,
                        readTime = LocalDateTime.now()
                    )
                    messageReadStatusRepository.save(readStatus)
                    println("✅ Marked message ${message.id} as read")
                }
            }
            
            // 通知会话更新
            notifySessionUpdate(userId)
            
        } catch (e: Exception) {
            println("❌ Error marking private messages as read: ${e.message}")
            e.printStackTrace()
            throw e
        }
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
        try {
            println("⭐ Getting message sessions for user $userId")
            
            val privateMessages = messageRepository.findLatestPrivateMessagesByUser(userId)
            println("📬 Found ${privateMessages.size} private messages")
            
            val groupMessages = messageRepository.findLatestGroupMessagesByUser(userId)
            println("👥 Found ${groupMessages.size} group messages")
            
            val sessions = mutableListOf<MessageSessionDTO>()

            // Handle private chat sessions
            privateMessages.forEach { message ->
                try {
                    val partnerId = if (message.sender.id == userId) message.receiver?.id else message.sender.id
                    val partner = if (message.sender.id == userId) message.receiver else message.sender
                    
                    partner?.let { user ->
                        val unreadCount = if (message.sender.id == userId) {
                            0
                        } else {
                            messageRepository.countUnreadMessages(userId, message.sender.id)
                        }
                        sessions.add(MessageSessionDTO(
                            id = message.id,
                            partnerId = user.id,
                            partnerName = user.nickname ?: user.username ?: "Unknown",
                            lastMessage = message.content,
                            lastMessageTime = message.timestamp,
                            unreadCount = unreadCount,
                            type = MessageSessionInfo.Type.PRIVATE,
                            partnerAvatar = if (user.avatarUrl != null) "/api/users/${user.id}/avatar" else null
                        ))
                        println("✅ Added private session with ${user.nickname ?: user.username}")
                    }
                } catch (e: Exception) {
                    println("❌ Error processing private message ${message.id}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Handle group chat sessions
            groupMessages.forEach { message ->
                try {
                    message.group?.let { group ->
                        val unreadCount = messageRepository.countUnreadGroupMessages(userId, group.id)
                        sessions.add(MessageSessionDTO(
                            id = message.id,
                            partnerId = group.id,
                            partnerName = group.name,
                            lastMessage = message.content,
                            lastMessageTime = message.timestamp,
                            unreadCount = unreadCount,
                            type = MessageSessionInfo.Type.GROUP,
                            partnerAvatar = if (group.avatarUrl != null) "/api/users/${group.id}/avatar" else null
                        ))
                        println("✅ Added group session for ${group.name}")
                    }
                } catch (e: Exception) {
                    println("❌ Error processing group message ${message.id}: ${e.message}")
                    e.printStackTrace()
                }
            }

            val sortedSessions = sessions.sortedByDescending { it.lastMessageTime }
            println("📊 Returning ${sortedSessions.size} total sessions")
            return sortedSessions
            
        } catch (e: Exception) {
            println("❌ Error getting message sessions: ${e.message}")
            e.printStackTrace()
            throw e
        }
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

    private fun notifySessionUpdate(userId: Long) {
        val sessions = getMessageSessions(userId)
        applicationEventPublisher.publishEvent(SessionsUpdateEvent(userId, sessions))
    }

    fun convertToMessageDTO(message: Message): MessageDTO {
        val sender = userRepository.findById(message.sender.id).orElse(null)
        
        return MessageDTO(
            id = message.id,
            content = message.content,
            senderId = message.sender.id,
            senderName = sender?.username ?: "Unknown",
            senderNickname = sender?.nickname,
            receiverId = message.receiver?.id,
            receiverName = if (message.receiver?.id != null) userRepository.findById(message.receiver!!.id).orElse(null)?.username else null,
            groupId = message.group?.id,
            type = message.type,
            fileUrl = message.fileUrl,
            timestamp = message.timestamp
        )
    }
}