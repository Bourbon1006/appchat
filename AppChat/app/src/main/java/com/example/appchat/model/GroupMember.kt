import java.time.LocalDateTime

data class GroupMember(
    val userId: Long,
    val groupId: Long,
    val isAdmin: Boolean,
    val joinedAt: LocalDateTime
) 