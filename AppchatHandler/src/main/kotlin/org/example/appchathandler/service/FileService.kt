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

@Service
class FileService {
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    
    @Value("\${file.upload.dir}")
    private lateinit var uploadDirPath: String
    
    private val uploadDir by lazy {
        File(System.getProperty("user.dir"), uploadDirPath).apply {
            if (!exists()) {
                logger.info("Creating upload directory at: ${absolutePath}")
                if (!mkdirs()) {
                    logger.error("Failed to create upload directory")
                    throw IOException("Failed to create upload directory")
                }
            }
            if (!canWrite()) {
                logger.error("Upload directory is not writable")
                throw IOException("Upload directory is not writable")
            }
            logger.info("Using upload directory: ${absolutePath}")
        }
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
                val file = uploadDir.listFiles()?.find { it.nameWithoutExtension == fileId }
                file?.delete()
                true
            } else false
        }
    }

    fun saveFile(file: MultipartFile): FileDTO {
        if (file.isEmpty) {
            logger.error("File is empty")
            throw IllegalArgumentException("File is empty")
        }

        logger.info("Saving file: ${file.originalFilename}, size: ${file.size}, contentType: ${file.contentType}")
        
        try {
            val fileId = UUID.randomUUID().toString()
            val extension = file.originalFilename?.substringAfterLast('.', "")
            val filename = "$fileId.$extension"
            val targetFile = File(uploadDir, filename)
            
            logger.info("Saving to: ${targetFile.absolutePath}")
            
            // 直接使用输入流复制文件，不做任何转换
            file.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!targetFile.exists()) {
                logger.error("File was not saved successfully")
                throw IOException("Failed to save file")
            }
            
            logger.info("File saved successfully at: ${targetFile.absolutePath}")
            
            fileExpirationMap[fileId] = System.currentTimeMillis() + EXPIRATION_TIME
            
            return FileDTO(
                id = fileId,
                filename = file.originalFilename ?: filename,
                url = "/api/files/download/$fileId",
                size = targetFile.length(),
                contentType = file.contentType ?: "application/octet-stream"
            )
        } catch (e: Exception) {
            logger.error("Error saving file", e)
            throw RuntimeException("Failed to save file: ${e.message}", e)
        }
    }

    fun getFile(fileId: String): FileResource {
        val file = uploadDir.listFiles()?.find { it.nameWithoutExtension == fileId }
            ?: throw NoSuchElementException("File not found")
            
        // 更新文件过期时间
        fileExpirationMap[fileId] = System.currentTimeMillis() + EXPIRATION_TIME
            
        return FileResource(
            filename = file.name,
            resource = FileSystemResource(file)
        )
    }

    fun markFileDownloaded(fileId: String) {
        // 文件被下载后，设置较短的过期时间
        fileExpirationMap[fileId] = System.currentTimeMillis() + 60 * 1000 // 1分钟后删除
    }
} 