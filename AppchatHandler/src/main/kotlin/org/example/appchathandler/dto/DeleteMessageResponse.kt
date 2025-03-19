package org.example.appchathandler.dto

data class DeleteMessageResponse(
    val success: Boolean,
    val message: String? = null
) 