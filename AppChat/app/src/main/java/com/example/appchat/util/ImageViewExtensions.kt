package com.example.appchat.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.R
import com.example.appchat.api.ApiClient

fun ImageView.loadAvatar(avatarUrl: String?) {
    val fullUrl = if (avatarUrl?.startsWith("http") == true) {
        avatarUrl
    } else {
        "${ApiClient.BASE_URL}${avatarUrl}"
    }

    Glide.with(this)
        .load(fullUrl)
        .apply(RequestOptions.circleCropTransform())
        .placeholder(R.drawable.default_avatar)
        .error(R.drawable.default_avatar)
        .into(this)
} 