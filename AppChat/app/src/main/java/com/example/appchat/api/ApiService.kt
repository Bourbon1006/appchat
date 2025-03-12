package com.example.appchat.api

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
    @GET("/api/groups/user/{userId}")
    fun getUserGroups(@Path("userId") userId: Long): Call<List<Group>>

    @POST("/api/groups")
    fun createGroup(@Body request: CreateGroupRequest): Call<Group>

    @GET("groups/{groupId}")
    fun getGroupById(@Path("groupId") groupId: Long): Call<Group>

    @POST("/api/groups/{groupId}/members/{userId}")
    fun addGroupMember(
        @Path("groupId") groupId: Long,
        @Path("userId") userId: Long
    ): Call<Group>

    @DELETE("/api/groups/{groupId}/members/{userId}")
    fun removeGroupMember(
        @Path("groupId") groupId: Long,
        @Path("userId") userId: Long
    ): Call<Group>

    @PUT("groups/{groupId}")
    fun updateGroup(@Path("groupId") groupId: Long, @Body group: Group): Call<Group>

    @GET("api/messages/group/{groupId}")
    suspend fun getGroupMessages(
        @Path("groupId") groupId: Long
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
    fun uploadFile(
        @Part file: MultipartBody.Part
    ): Call<FileDTO>

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

    @Multipart
    @POST("api/users/{userId}/avatar")
    fun uploadAvatar(
        @Path("userId") userId: Long,
        @Part avatar: MultipartBody.Part
    ): Call<UserDTO>

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

    @POST("api/messages/read")
    suspend fun markSessionAsRead(
        @Query("userId") userId: Long,
        @Query("partnerId") partnerId: Long,
        @Query("type") type: String
    ): Response<Unit>

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
}

data class DeleteMessageResponse(
    val isFullyDeleted: Boolean  // 表示消息是否已被所有相关用户删除
)