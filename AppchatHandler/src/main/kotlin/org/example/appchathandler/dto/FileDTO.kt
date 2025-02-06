package org.example.appchathandler.dto

data class FileDTO(
    val id: Long = 0,
    val filename: String,
    val url: String,
    val size: Long = 0,
    val contentType: String = "application/octet-stream",
    val errorMessage: String? = null
) 