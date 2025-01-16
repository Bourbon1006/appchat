package org.example.appchathandler.service

import org.example.appchathandler.entity.Message
import org.example.appchathandler.repository.MessageRepository
import org.example.appchathandler.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val userService: UserService
) {
    fun saveMessage(message: Message): Message {
        return messageRepository.save(message)
    }

    @Transactional(readOnly = true)
    fun getMessageHistory(userId: Long, otherUserId: Long? = null): List<Message> {
        return if (otherUserId == null) {
            messageRepository.findByReceiverIsNullOrderByTimestampDesc()
        } else {
            messageRepository.findByUserMessages(userId, otherUserId)
        }
    }

    fun markAsRead(messageId: Long) {
        messageRepository.findById(messageId).ifPresent {
            it.isRead = true
            messageRepository.save(it)
        }
    }

    fun deleteMessage(messageId: Long, userId: Long) {
        val message = messageRepository.findById(messageId).orElse(null)
        if (message?.sender?.id == userId) {
            messageRepository.deleteById(messageId)
        }
    }
} 