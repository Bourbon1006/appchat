data class CreateGroupRequest(
    val name: String,
    val creatorId: Long,
    val memberIds: List<Long>
) 