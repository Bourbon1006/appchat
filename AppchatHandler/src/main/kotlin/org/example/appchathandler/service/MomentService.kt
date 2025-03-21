package org.example.appchathandler.service

import LikeUserDTO
import org.example.appchathandler.dto.MomentDTO
import org.example.appchathandler.dto.MomentCommentDTO
import org.example.appchathandler.dto.UserDTO
import org.example.appchathandler.entity.*
import org.example.appchathandler.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MomentService(
    private val momentRepository: MomentRepository,
    private val momentLikeRepository: MomentLikeRepository,
    private val momentCommentRepository: MomentCommentRepository,
    private val userService: UserService,
) {
    @Transactional
    fun createMoment(userId: Long, content: String, imageUrl: String?): MomentDTO {
        val user = userService.getUser(userId)
        val moment = Moment(
            user = user,
            content = content,
            imageUrl = imageUrl
        )
        val savedMoment = momentRepository.save(moment)
        return convertToDTO(savedMoment, userId)
    }

    fun getFriendMoments(userId: Long): List<MomentDTO> {
        val friends = userService.getUserContacts(userId)
        val userIds = friends.map { it.id } + userId
        return momentRepository.findByUserIdInOrderByCreateTimeDesc(userIds)
            .map { convertToDTO(it, userId) }
    }

    fun getUserMoments(userId: Long): List<MomentDTO> {
        return momentRepository.findByUserIdOrderByCreateTimeDesc(userId)
            .map { convertToDTO(it, userId) }
    }

    @Transactional
    fun likeMoment(momentId: Long, userId: Long) {
        val moment = momentRepository.findById(momentId).orElseThrow()
        val likeId = MomentLikeId(momentId, userId)
        
        if (!momentLikeRepository.existsById(likeId)) {
            momentLikeRepository.save(MomentLike(likeId))
            moment.likeCount++
            momentRepository.save(moment)
        }
    }

    @Transactional
    fun unlikeMoment(momentId: Long, userId: Long) {
        val moment = momentRepository.findById(momentId).orElseThrow()
        val likeId = MomentLikeId(momentId, userId)
        
        if (momentLikeRepository.existsById(likeId)) {
            momentLikeRepository.deleteById(likeId)
            moment.likeCount--
            momentRepository.save(moment)
        }
    }

    @Transactional
    fun addComment(momentId: Long, userId: Long, content: String): MomentCommentDTO {
        val moment = momentRepository.findById(momentId).orElseThrow()
        val user = userService.getUser(userId)
        
        val comment = MomentComment(
            moment = moment,
            user = user,
            content = content
        )
        
        val savedComment = momentCommentRepository.save(comment)
        moment.commentCount++
        momentRepository.save(moment)
        
        return convertCommentToDTO(savedComment)
    }

    @Transactional
    fun deleteMoment(momentId: Long, userId: Long) {
        val moment = momentRepository.findById(momentId).orElseThrow {
            RuntimeException("动态不存在")
        }

        // 检查是否是动态作者
        if (moment.user.id != userId) {
            throw RuntimeException("无权限删除此动态")
        }

        // 删除相关的点赞记录
        momentLikeRepository.deleteByMomentId(momentId)

        // 删除相关的评论
        momentCommentRepository.deleteByMomentId(momentId)

        // 删除动态本身
        momentRepository.delete(moment)
    }

    private fun convertToDTO(moment: Moment, currentUserId: Long): MomentDTO {
        val comments = momentCommentRepository.findByMomentIdOrderByCreateTimeAsc(moment.id)
        val isLiked = momentLikeRepository.existsById(MomentLikeId(moment.id, currentUserId))
        
        // 获取点赞用户列表
        val likeUsers = momentLikeRepository.findByMomentId(moment.id).map { like ->
            val user = userService.getUser(like.id.userId)
            UserDTO(
                id = user.id,
                username = user.username,
                nickname = user.nickname,
                avatarUrl = user.avatarUrl,
                onlineStatus = user.onlineStatus ?: 0
            )
        }
        
        return MomentDTO(
            id = moment.id,
            userId = moment.user.id,
            username = moment.user.username,
            userNickname = moment.user.nickname,
            userAvatar = if (moment.user.avatarUrl != null) "/api/users/${moment.user.id}/avatar" else null,
            content = moment.content,
            imageUrl = moment.imageUrl,
            createTime = moment.createTime,
            likeCount = moment.likeCount,
            commentCount = moment.commentCount,
            isLiked = isLiked,
            comments = comments.map { convertCommentToDTO(it) },
            likeUsers = likeUsers
        )
    }

    private fun convertCommentToDTO(comment: MomentComment): MomentCommentDTO {
        return MomentCommentDTO(
            id = comment.id,
            userId = comment.user.id,
            username = comment.user.username,
            userNickname = comment.user.nickname,
            userAvatar = if (comment.user.avatarUrl != null) "/api/users/${comment.user.id}/avatar" else null,
            content = comment.content,
            createTime = comment.createTime
        )
    }
} 