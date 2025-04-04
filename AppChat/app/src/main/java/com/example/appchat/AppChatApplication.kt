package com.example.appchat

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.appchat.api.ApiClient
import com.example.appchat.service.WebSocketService

class AppChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        
        // 启动前台服务
        val serviceIntent = Intent(this, WebSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
} 