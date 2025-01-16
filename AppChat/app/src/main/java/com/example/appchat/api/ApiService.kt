package com.example.appchat.api

import com.example.appchat.model.LoginRequest
import com.example.appchat.model.RegisterRequest
import com.example.appchat.model.AuthResponse
import com.example.appchat.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/auth/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>

    @GET("api/users")
    fun getUsers(): Call<List<User>>
} 