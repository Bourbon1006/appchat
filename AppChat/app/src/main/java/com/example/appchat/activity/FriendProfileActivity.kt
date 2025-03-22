package com.example.appchat.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.appchat.R

class FriendProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_profile)

        val friendId = intent.getLongExtra("friendId", -1)
        val friendName = intent.getStringExtra("friendName") ?: ""
        val friendAvatar = intent.getStringExtra("friendAvatar") ?: ""

        // 初始化视图
        findViewById<TextView>(R.id.friendNameTextView).text = friendName
        val avatarImageView = findViewById<ImageView>(R.id.friendAvatarImageView)
        Glide.with(this)
            .load(friendAvatar)
            .placeholder(R.drawable.default_avatar)
            .into(avatarImageView)

        // 删除好友按钮
        findViewById<Button>(R.id.deleteFriendButton).setOnClickListener {
            // TODO: 调用删除好友的 API
            finish()
        }
    }
} 