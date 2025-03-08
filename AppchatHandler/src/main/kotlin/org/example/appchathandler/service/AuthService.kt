package org.example.appchathandler.service

interface AuthService {
    fun login(username: String, password: String): Map<String, Any>
    fun register(username: String, password: String, email: String): Map<String, Any>
} 