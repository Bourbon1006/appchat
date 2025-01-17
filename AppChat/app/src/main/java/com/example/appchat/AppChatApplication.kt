package com.example.appchat

import android.app.Application
import com.example.appchat.api.ApiClient

class AppChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
} 