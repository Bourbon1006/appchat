package org.example.appchathandler.service

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File

@Service
class FileService {
    private val uploadDir = "uploads"

    init {
        File(uploadDir).mkdirs()
    }

    fun saveFile(file: MultipartFile): String {
        val fileName = "${System.currentTimeMillis()}_${file.originalFilename}"
        val path = "$uploadDir/$fileName"
        file.transferTo(File(path))
        return fileName
    }

    fun getFile(fileName: String): File {
        return File("$uploadDir/$fileName")
    }
} 