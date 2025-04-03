package com.example.appchat.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.appchat.R

/**
 * 加载用户或群组头像的扩展函数
 */
fun ImageView.loadAvatar(url: String) {
    Glide.with(this.context)
        .load(url)
        .skipMemoryCache(true)  // 不使用内存缓存
        .diskCacheStrategy(DiskCacheStrategy.NONE)  // 不使用磁盘缓存
        .circleCrop()  // 圆形裁剪
        .placeholder(R.drawable.default_avatar)  // 加载中显示的占位图
        .error(R.drawable.default_avatar)  // 加载失败显示的图片
        .into(this)
} 