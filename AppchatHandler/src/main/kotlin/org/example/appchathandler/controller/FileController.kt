package org.example.appchathandler.controller

import org.example.appchathandler.dto.FileDTO
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

@RestController
@RequestMapping("/api/files")
class FileController(private val fileService: FileService) {
    private val logger = LoggerFactory.getLogger(FileController::class.java)
    
    data class ErrorResponse(
        val message: String,
        val details: String? = null
    )
    
    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<Any> {
        logger.info("Received file upload request: ${file.originalFilename}, size: ${file.size}")
        return try {
            val savedFile = fileService.saveFile(file)
            logger.info("File uploaded successfully: ${savedFile.filename}")
            ResponseEntity.ok(savedFile)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid file upload request", e)
            ResponseEntity.badRequest()
                .body(ErrorResponse(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            logger.error("Error uploading file", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse(
                    message = "Failed to upload file",
                    details = e.message
                ))
        }
    }
    
    @GetMapping("/download/{fileId}")
    fun downloadFile(@PathVariable fileId: String): ResponseEntity<Resource> {
        logger.info("Received file download request: $fileId")
        return try {
            val file = fileService.getFile(fileId)
            fileService.markFileDownloaded(fileId)
            
            // 对文件名进行 URL 编码
            val encodedFilename = URLEncoder.encode(file.filename, "UTF-8").replace("+", "%20")
            
            // 获取正确的 MIME 类型
            val mimeType = when (file.filename.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE
                "png" -> MediaType.IMAGE_PNG_VALUE
                "gif" -> MediaType.IMAGE_GIF_VALUE
                "pdf" -> MediaType.APPLICATION_PDF_VALUE
                else -> MediaType.APPLICATION_OCTET_STREAM_VALUE
            }
            
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''$encodedFilename"
                )
                .body(file.resource)
        } catch (e: Exception) {
            logger.error("Error downloading file", e)
            ResponseEntity.notFound().build()
        }
    }
} 