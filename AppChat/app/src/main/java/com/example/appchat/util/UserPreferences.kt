package com.example.appchat.util

import android.content.Context

object UserPreferences {
    private const val PREF_NAME = "AppChatPrefs"
    private const val KEY_USER_ID = "userId"
    private const val KEY_USERNAME = "username"
    private const val KEY_TOKEN = "token"

    fun saveUserInfo(context: Context, userId: Long, username: String, token: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            token?.let { putString(KEY_TOKEN, it) }  // 只在 token 不为空时保存
            apply()
        }
    }

    fun getUserId(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1)
    }

    fun getUsername(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, "") ?: ""
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
} 