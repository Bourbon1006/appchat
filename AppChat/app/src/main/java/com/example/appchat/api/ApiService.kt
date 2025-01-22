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
    fun searchUsers(@Query("keyword") keyword: String): Call<List<User>>

    @GET("api/users/{userId}/contacts")
    fun getUserContacts(@Path("userId") userId: Long): Call<List<User>>

    @POST("api/friend-requests")
    fun sendFriendRequest(@Body request: Map<String, Long>): Call<FriendRequest>

    @PUT("api/friend-requests/{requestId}")
    fun handleFriendRequest(
        @Path("requestId") requestId: Long,
        @Body request: Map<String, Boolean>
    ): Call<FriendRequest>

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
    fun getGroupMessages(@Path("groupId") groupId: Long): Call<List<ChatMessage>>

    @GET("api/messages/private")
    fun getPrivateMessages(
        @Query("userId") userId: Long,
        @Query("otherId") otherId: Long
    ): Call<List<ChatMessage>>

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
    fun uploadFile(@Part file: MultipartBody.Part): Call<FileDTO>

    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: Long,
        @Query("userId") userId: Long
    ): Response<Unit>

    @DELETE("messages/all")
    suspend fun deleteAllMessages(
        @Query("userId") userId: Long,
        @Query("otherId") otherId: Long
    ): Response<Unit>
}