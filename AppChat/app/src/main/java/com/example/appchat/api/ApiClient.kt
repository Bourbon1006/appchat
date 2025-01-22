package com.example.appchat.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import com.example.appchat.R
import com.google.gson.GsonBuilder
import java.time.LocalDateTime

object ApiClient {
    private lateinit var retrofit: Retrofit

    fun init(context: Context) {
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(context.getString(R.string.server_url_format).format(
                context.getString(R.string.server_ip),
                context.getString(R.string.server_port)
            ))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
    }

    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
} 