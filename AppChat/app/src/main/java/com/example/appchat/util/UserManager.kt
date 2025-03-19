package com.example.appchat.util

import com.example.appchat.model.User

object UserManager {
    private var currentUser: User? = null

    fun setCurrentUser(user: User) {
        currentUser = user
    }

    fun getCurrentUser(): User? {
        return currentUser
    }

    fun clearCurrentUser() {
        currentUser = null
    }
} 