/*
package com.example.appchat.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import com.example.appchat.model.MessageSessionDTO

object RetrofitClient {
    private const val BASE_URL = "http://192.168.31.194:8080/"  // 替换为你的服务器地址

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val messageService: MessageService = retrofit.create(MessageService::class.java)
}

interface MessageService {
    @GET("api/messages/sessions")
    suspend fun getMessageSessions(@Query("userId") userId: Long): Response<List<MessageSessionDTO>>
} */
