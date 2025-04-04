package com.example.appchat.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.appchat.R
import com.example.appchat.activity.ChatActivity
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.websocket.WebSocketManager

class WebSocketService : Service() {
    private val CHANNEL_ID = "WebSocket_Service_Channel"
    private val NOTIFICATION_ID = 1

    companion object {
        private const val MESSAGE_NOTIFICATION_ID = 2
        private const val MESSAGE_CHANNEL_ID = "Message_Channel"
    }

    private lateinit var notificationManager: NotificationManager
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        createMessageChannel()
        WebSocketManager.setWebSocketService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 及以上版本
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WebSocket Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the WebSocket connection alive"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppChat")
            .setContentText("保持连接中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun createMessageChannel() {
        val channel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "消息通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "接收新消息通知"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showMessageNotification(message: ChatMessage) {
        // 创建打开聊天界面的 Intent
        val chatIntent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_type", if (message.groupId != null) "GROUP" else "PRIVATE")
            putExtra("receiver_id", if (message.groupId != null) message.groupId else message.senderId)
            putExtra("receiver_name", if (message.groupId != null) {
                message.groupName ?: "群聊"
            } else {
                message.senderName
            })
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(if (message.groupId != null) {
                "${message.groupName ?: "群聊"}: ${message.senderName}"
            } else {
                message.senderName
            })
            .setContentText(when (message.type) {
                MessageType.TEXT -> message.content
                MessageType.IMAGE -> "[图片]"
                MessageType.VIDEO -> "[视频]"
                MessageType.FILE -> "[文件]"
                MessageType.TIME -> message.content
            })
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // 添加通知组
            .setGroup(if (message.groupId != null) "group_${message.groupId}" else "private_${message.senderId}")
            .build()

        // 使用消息ID作为通知ID，确保每条消息都有唯一的通知
        val notificationId = message.id?.toInt()

        notificationId?.let { notificationManager.notify(it, notification) }

        // 如果有多条未读消息，显示摘要通知
        val summaryNotification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle("AppChat")
            .setContentText("您有新的消息")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(if (message.groupId != null) "group_${message.groupId}" else "private_${message.senderId}")
            .setGroupSummary(true)
            .build()

        // 使用固定的ID显示摘要通知
        notificationManager.notify(0, summaryNotification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.disconnect()
    }
} 