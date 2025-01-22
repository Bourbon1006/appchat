package org.example.appchathandler.dto

data class FileDTO(
    val id: String,
    val filename: String,
    val url: String,
    val size: Long,
    val contentType: String
) 