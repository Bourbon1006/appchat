package com.example.appchat.api

import GroupMember
import com.example.appchat.model.*
import com.example.appchat.model.CreateGroupRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.ResponseBody
import java.time.LocalDateTime
import okhttp3.RequestBody

interface ApiService {
    @POST("api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/auth/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>

    @GET("api/users")
    fun getUsers(): Call<List<User>>

    @GET("api/users/online")
    fun getOnlineUsers(): Call<List<User>>

    @GET("api/users/search")
    fun searchUsers(@Query("keyword") keyword: String): Call<List<UserDTO>>

    @GET("api/users/{userId}/contacts")
    fun getUserContacts(@Path("userId") userId: Long): Call<List<UserDTO>>
    /*
    @POST("api/friend-requests")
    fun sendFriendRequest(@Body request: Map<String, Long>): Call<FriendRequest>
    @PUT("api/friend-requests/{requestId}")
    fun handleFriendRequest(
        @Path("requestId") requestId: Long,
        @Body request: Map<String, Boolean>
    ): Call<FriendRequest>
    */
    @GET("api/groups/user/{userId}")
    fun getUserGroups(@Path("userId") userId: Long): Call<List<Group>>

    @POST("api/groups")
    fun createGroup(@Body request: CreateGroupRequest): Call<Group>

    @GET("groups/{groupId}")
    fun getGroupById(@Path("groupId") groupId: Long): Call<Group>

    @POST("api/groups/{groupId}/members/{userId}")
    suspend fun addGroupMember(
        @Path("groupId") groupId: Long,
        @Path("userId") userId: Long
    ): Response<GroupMember>

    @DELETE("api/groups/{groupId}/members/{memberId}")
    suspend fun removeGroupMember(
        @Path("groupId") groupId: Long,
        @Path("memberId") memberId: Long
    ): Response<Void>

    @PUT("groups/{groupId}")
    fun updateGroup(@Path("groupId") groupId: Long, @Body group: Group): Call<Group>

    @GET("api/messages/group/{groupId}")
    suspend fun getGroupMessages(
        @Path("groupId") groupId: Long,
        @Query("userId") userId: Long
    ): List<ChatMessage>

    @GET("api/messages/private")
    suspend fun getPrivateMessages(
        @Query("userId") userId: Long,
        @Query("otherId") otherId: Long
    ): List<ChatMessage>

    @GET("api/messages/user/{userId}")
    fun getUserMessages(@Path("userId") userId: Long): Call<List<ChatMessage>>

    @GET("api/messages/search")
    fun searchMessages(
        @Query("userId") userId: Long,
        @Query("keyword") keyword: String
    ): Call<List<ChatMessage>>

    @GET("api/messages/date-range")
    fun getMessagesByDateRange(
        @Query("userId") userId: Long,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Call<List<ChatMessage>>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): Response<FileUploadResponse>

    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: Long,
        @Query("userId") userId: Long
    ): Response<DeleteMessageResponse>

    @DELETE("messages/all")
    suspend fun deleteAllMessages(
        @Query("userId") userId: Long,
        @Query("otherId") otherId: Long
    ): Response<Unit>

    @DELETE("messages/batch")
    suspend fun deleteMessages(@Query("messageIds") messageIds: List<Long>): Response<Unit>

    @Multipart
    @POST("api/users/{userId}/avatar")
    suspend fun uploadAvatar(
        @Path("userId") userId: Long,
        @Part avatar: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("api/users/{userId}/avatar")
    fun getUserAvatar(@Path("userId") userId: Long): Call<ResponseBody>

    @GET("api/users/{userId}/avatar")
    fun getAvatar(@Path("userId") userId: Long): Call<ResponseBody>

    @PUT("api/users/{userId}")
    fun updateUser(
        @Path("userId") userId: Long,
        @Body request: UpdateUserRequest
    ): Call<UserDTO>

    @GET("api/users/{userId}")
    fun getUser(@Path("userId") userId: Long): Call<UserDTO>

    @GET("api/messages/sessions")
    suspend fun getMessageSessions(
        @Query("userId") userId: Long
    ): List<MessageSession>

    @POST("/api/messages/read")
    suspend fun markSessionAsRead(
        @Query("userId") userId: Long,
        @Query("partnerId") partnerId: Long,
        @Query("type") type: String
    ): Response<Void>

    @DELETE("api/messages/sessions")
    suspend fun deleteSession(
        @Query("userId") userId: Long,
        @Query("partnerId") partnerId: Long,
        @Query("type") type: String
    ): Response<Unit>

    @POST("api/messages/{messageId}/read")
    suspend fun markMessageAsRead(
        @Path("messageId") messageId: Long
    ): Response<Unit>

    @DELETE("api/friends")
    suspend fun deleteFriend(
        @Query("userId") userId: Long,
        @Query("friendId") friendId: Long
    ): Response<Unit>

    @DELETE("api/sessions/private")
    suspend fun deletePrivateSession(
        @Query("userId") userId: Long,
        @Query("partnerId") partnerId: Long
    ): Response<Unit>

    @GET("api/users/{userId}/contacts")
    suspend fun getFriends(@Path("userId") userId: Long): Response<List<UserDTO>>

    @GET("api/moments/friends")
    suspend fun getFriendMoments(@Query("userId") userId: Long): List<Moment>

    @GET("api/moments/user/{userId}")
    suspend fun getUserMoments(@Path("userId") userId: Long): List<Moment>

    @POST("api/moments/{momentId}/like")
    suspend fun likeMoment(
        @Path("momentId") momentId: Long,
        @Query("userId") userId: Long
    )

    @DELETE("api/moments/{momentId}/like")
    suspend fun unlikeMoment(
        @Path("momentId") momentId: Long,
        @Query("userId") userId: Long
    )

    @POST("api/moments/{momentId}/comments")
    suspend fun addComment(
        @Path("momentId") momentId: Long,
        @Query("userId") userId: Long,
        @Body request: CreateCommentRequest
    ): MomentComment

    @POST("api/moments")
    suspend fun createMoment(
        @Query("userId") userId: Long,
        @Body request: CreateMomentRequest
    ): Response<Moment>

    @DELETE("api/moments/{momentId}")
    suspend fun deleteMoment(
        @Path("momentId") momentId: Long,
        @Query("userId") userId: Long
    )

    @POST("api/friends/handle")
    suspend fun handleFriendRequest(
        @Query("requestId") requestId: Long,
        @Query("accept") accept: Boolean
    ): Response<Unit>

    @GET("api/friends/pending/{userId}")
    suspend fun getPendingRequests(@Path("userId") userId: Long): Response<List<FriendRequest>>

    @PUT("api/users/{userId}/status")
    suspend fun updateOnlineStatus(
        @Path("userId") userId: Long,
        @Query("status") status: Int
    ): Response<Unit>

    @PUT("api/users/{userId}/nickname")
    suspend fun updateNickname(
        @Path("userId") userId: Long,
        @Body request: UpdateNicknameRequest
    ): Response<Void>

    @PUT("api/users/{userId}/password")
    suspend fun updatePassword(
        @Path("userId") userId: Long,
        @Body request: UpdatePasswordRequest
    ): Response<Unit>

    @PUT("api/messages/read")
    suspend fun markMessagesAsRead(
        @Query("sessionId") sessionId: Long,
        @Query("userId") userId: Long
    ): Response<Unit>

    @POST("api/groups/{groupId}/leave")
    suspend fun leaveGroup(
        @Path("groupId") groupId: Long,
        @Query("userId") userId: Long
    ): Response<Void>

    @GET("api/groups/{groupId}")
    suspend fun getGroupDetails(@Path("groupId") groupId: Long): Response<Group>

    @GET("api/groups/{groupId}/members")
    suspend fun getGroupMembers(@Path("groupId") groupId: Long): Response<List<UserDTO>>

    @PUT("api/groups/{groupId}/name")
    suspend fun updateGroupName(
        @Path("groupId") groupId: Long, 
        @Query("name") newName: String
    ): Response<Group>

    @Multipart
    @POST("api/groups/{groupId}/avatar")
    suspend fun uploadGroupAvatar(
        @Path("groupId") groupId: Long,
        @Part avatar: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("api/friends/pending/{userId}")
    suspend fun getFriendRequests(
        @Path("userId") userId: Long
    ): Response<List<UserDTO>>

    @POST("api/friends/handle")
    suspend fun acceptFriendRequest(
        @Query("requestId") requestId: Long,
        @Query("accept") accept: Boolean = true
    ): Response<Unit>

    @POST("api/friends/handle")
    suspend fun rejectFriendRequest(
        @Query("requestId") requestId: Long,
        @Query("accept") accept: Boolean = false
    ): Response<Unit>
}

data class DeleteMessageResponse(
    val isFullyDeleted: Boolean  // 表示消息是否已被所有相关用户删除
)

data class CreateMomentRequest(
    val content: String,
    val imageUrl: String?
)

data class CreateCommentRequest(
    val content: String
)

data class UpdateNicknameRequest(
    val nickname: String
)

data class UpdatePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)