data class UpdateNicknameRequest(
    val nickname: String
)

data class UpdatePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

data class UserDTO(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val onlineStatus: Int? = 0
) 