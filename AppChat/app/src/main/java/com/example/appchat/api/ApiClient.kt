package com.example.appchat.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import com.example.appchat.R
import com.google.gson.GsonBuilder
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ApiClient {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val BASE_URL: String
        get() {
            if (!::context.isInitialized) {
                throw IllegalStateException("ApiClient not initialized. Call ApiClient.init(context) first")
            }
            
            val serverIp = context.getString(R.string.server_ip)
            val serverPort = context.getString(R.string.server_port)
            val urlFormat = context.getString(R.string.server_http_url_format)
            
            return String.format(urlFormat, serverIp, serverPort) + "/"
        }

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .create()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
} 