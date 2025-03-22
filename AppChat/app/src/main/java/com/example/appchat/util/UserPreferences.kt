package com.example.appchat.util

import android.content.Context
import com.example.appchat.R

object UserPreferences {
    private const val PREF_NAME = "user_preferences"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_USER_NICKNAME = "user_nickname"

    fun saveUserData(context: Context, userId: Long, token: String, username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            apply()
        }
    }

    fun saveUserId(context: Context, userId: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, userId)
            .apply()
        println("✅ Saved userId: $userId")
    }

    fun getUserId(context: Context): Long {
        val userId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1)
        println("📱 Retrieved userId: $userId")
        return userId
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getAvatarUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AVATAR_URL, null)
    }

    fun setAvatarUrl(context: Context, url: String?) {
        val fixedUrl = url?.replace("//api", "/api")
        
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AVATAR_URL, fixedUrl).apply()
    }

    fun clearUserData(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        println("🗑️ Cleared user data")
    }

    fun saveUserNickname(context: Context, nickname: String?) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_USER_NICKNAME, nickname).apply()
    }

    fun getUserNickname(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_USER_NICKNAME, null)
    }

    fun saveUsername(context: Context, username: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, username)
            .apply()
        println("✅ Saved username: $username")
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        println("✅ Saved token")
    }
} 