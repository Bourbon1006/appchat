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

    fun getUserId(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_USER_ID, -1)
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun saveUserNickname(context: Context, nickname: String?) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_USER_NICKNAME, nickname).apply()
    }

    fun getUserNickname(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_USER_NICKNAME, null)
    }
} 