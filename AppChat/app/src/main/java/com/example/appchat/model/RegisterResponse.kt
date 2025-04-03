data class RegisterResponse(
    val userId: Long,
    val username: String,
    val token: String? = null  // 标记为可空，因为注册响应可能不包含 token
) 