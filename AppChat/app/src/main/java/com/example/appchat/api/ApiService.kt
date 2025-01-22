package com.example.appchat.api

import com.example.appchat.model.LoginRequest
import com.example.appchat.model.RegisterRequest
import com.example.appchat.model.AuthResponse
import com.example.appchat.model.User
import com.example.appchat.model.FriendRequest
import com.example.appchat.model.Group
import com.example.appchat.model.CreateGroupRequest
import com.example.appchat.model.ChatMessage
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.DELETE

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
        @Query("user1Id") user1Id: Long,
        @Query("user2Id") user2Id: Long
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
} 