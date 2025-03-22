package com.example.appchat.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.appchat.R

fun ImageView.loadAvatar(url: String?) {
    Glide.with(this)
        .load(url)
        .circleCrop()
        .skipMemoryCache(true)  // 跳过内存缓存
        .diskCacheStrategy(DiskCacheStrategy.NONE)  // 跳过磁盘缓存
        .placeholder(R.drawable.default_avatar)
        .error(R.drawable.default_avatar)
        .into(this)
} 