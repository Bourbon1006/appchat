package com.example.appchat.api

import com.example.appchat.model.LoginRequest
import com.example.appchat.model.RegisterRequest
import com.example.appchat.model.AuthResponse
import com.example.appchat.model.User
import com.example.appchat.model.FriendRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.http.PUT

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
} 