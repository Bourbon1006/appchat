package org.example.appchathandler.service

import org.example.appchathandler.dto.FileDTO
import org.example.appchathandler.model.FileResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.UUID
import org.springframework.core.io.FileSystemResource
import java.util.NoSuchElementException
import java.util.concurrent.ConcurrentHashMap
import java.util.Timer
import java.util.TimerTask
import org.slf4j.LoggerFactory
import java.io.IOException
import org.springframework.beans.factory.annotation.Value
import java.io.FileNotFoundException
import java.lang.RuntimeException
import org.example.appchathandler.dto.FileUploadResponse

@Service
class FileService(
    @Value("\${file.upload.dir}") private val uploadDirPath: String
) {
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    
    private val uploadDirectory = File(System.getProperty("user.dir"), uploadDirPath).apply {
        if (!exists()) {
            println("⭐ Creating uploads directory")
            if (mkdirs()) {
                println("✅ Created uploads directory: ${absolutePath}")
            } else {
                println("❌ Failed to create uploads directory")
            }
        }
        println("⭐ Upload directory: ${absolutePath}")
        println("⭐ Directory exists: ${exists()}")
        println("⭐ Directory readable: ${canRead()}")
        println("⭐ Directory writable: ${canWrite()}")
    }

    private val fileExpirationMap = ConcurrentHashMap<String, Long>()
    private val EXPIRATION_TIME = 24 * 60 * 60 * 1000L // 24小时

    init {
        // 启动定期清理任务
        startCleanupTask()
    }

    private fun startCleanupTask() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                cleanupExpiredFiles()
            }
        }, EXPIRATION_TIME, EXPIRATION_TIME)
    }

    private fun cleanupExpiredFiles() {
        val now = System.currentTimeMillis()
        fileExpirationMap.entries.removeIf { (fileId, expireTime) ->
            if (now > expireTime) {
                val file = uploadDirectory.listFiles()?.find { it.nameWithoutExtension == fileId }
                file?.delete()
                true
            } else false
        }
    }

    fun saveFile(file: MultipartFile): FileUploadResponse {
        try {
            // 确保上传目录存在
            if (!uploadDirectory.exists()) {
                uploadDirectory.mkdirs()
                uploadDirectory.setReadable(true, false)
                uploadDirectory.setWritable(true, false)
            }

            // 生成唯一文件名
            val originalFilename = file.originalFilename ?: "unknown"
            val extension = originalFilename.substringAfterLast('.', "")
            val uniqueFilename = "${UUID.randomUUID()}.$extension"
            
            // 保存文件
            val uploadedFile = File(uploadDirectory, uniqueFilename)
            file.transferTo(uploadedFile)
            uploadedFile.setReadable(true, false)
            println("✅ File saved to: ${uploadedFile.absolutePath}")

            // 构建文件URL
            val fileUrl = "/api/files/$uniqueFilename"
            println("✅ File URL: $fileUrl")

            return FileUploadResponse(
                fileName = uniqueFilename,
                url = fileUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to save file: ${e.message}")
            throw RuntimeException("Failed to save file", e)
        }
    }

    fun getFile(filename: String): File {
        val file = File(uploadDirectory, filename)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $filename")
        }
        return file
    }

    fun markFileDownloaded(fileId: String) {
        // 文件被下载后，设置较短的过期时间
        fileExpirationMap[fileId] = System.currentTimeMillis() + 60 * 1000 // 1分钟后删除
    }
} 