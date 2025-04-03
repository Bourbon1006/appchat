data class FriendGroup(
    val id: Long,
    val name: String,
    val user: UserDTO,
    val members: List<UserDTO>?
) 