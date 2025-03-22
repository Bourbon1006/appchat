package org.example.appchathandler.controller

import org.example.appchathandler.dto.FileDTO
import org.example.appchathandler.dto.FileUploadResponse
import org.example.appchathandler.service.FileService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.io.File
import java.nio.file.Files
import org.springframework.beans.factory.annotation.Value

@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService
) {
    private val logger = LoggerFactory.getLogger(FileController::class.java)
    
    data class ErrorResponse(
        val message: String,
        val details: String? = null
    )
    
    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<FileUploadResponse> {
        return try {
            val response = fileService.saveFile(file)
            println("✅ File uploaded: ${response.fileName}")
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            println("❌ File upload failed: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
    
    @GetMapping("/download/{filename}")
    fun downloadFile(@PathVariable filename: String): ResponseEntity<Resource> {
        println("⭐ Received download request for file: $filename")
        
        // 确保 uploads 目录存在
        val uploadDir = File("uploads").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        
        val file = File(uploadDir, filename)
        println("⭐ Looking for file at: ${file.absolutePath}")
        
        if (!file.exists()) {
            println("❌ File not found: ${file.absolutePath}")
            // 列出目录中的所有文件，帮助调试
            println("⭐ Files in uploads directory:")
            uploadDir.listFiles()?.forEach { f ->
                println("  - ${f.name} (${f.length()} bytes)")
            }
            
            // 尝试查找不带扩展名的文件
            val fileWithoutExt = uploadDir.listFiles()?.find { 
                it.nameWithoutExtension == filename 
            }
            if (fileWithoutExt != null) {
                println("✅ Found file without extension: ${fileWithoutExt.name}")
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${fileWithoutExt.name}\"")
                    .body(FileSystemResource(fileWithoutExt))
            }
            
            return ResponseEntity.notFound().build()
        }

        println("✅ File found, size: ${file.length()} bytes")
        
        val resource = FileSystemResource(file)
        val mediaType = try {
            val contentType = Files.probeContentType(file.toPath())
            println("⭐ Content type: $contentType")
            MediaType.parseMediaType(contentType ?: "application/octet-stream")
        } catch (e: Exception) {
            println("⚠️ Could not determine media type: ${e.message}")
            MediaType.APPLICATION_OCTET_STREAM
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.name}\"")
            .body(resource)
    }

    @GetMapping("/{filename}")
    fun getFile(@PathVariable filename: String): ResponseEntity<Resource> {
        return try {
            val file = fileService.getFile(filename)
            val resource = FileSystemResource(file)
            
            // 设置正确的 Content-Type
            val contentType = when {
                filename.endsWith(".jpg", true) || 
                filename.endsWith(".jpeg", true) -> "image/jpeg"
                filename.endsWith(".png", true) -> "image/png"
                filename.endsWith(".gif", true) -> "image/gif"
                else -> "application/octet-stream"
            }
            
            println("✅ Serving file: $filename with type: $contentType")
            
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource)
        } catch (e: Exception) {
            println("❌ Failed to serve file: ${e.message}")
            e.printStackTrace()
            ResponseEntity.notFound().build()
        }
    }
} 