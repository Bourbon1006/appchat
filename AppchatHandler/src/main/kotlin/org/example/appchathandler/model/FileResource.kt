package org.example.appchathandler.model

import org.springframework.core.io.Resource

data class FileResource(
    val filename: String,
    val resource: Resource
) 