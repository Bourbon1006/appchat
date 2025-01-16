package org.example.appchathandler.controller

import org.example.appchathandler.service.FileService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files

@RestController
@RequestMapping("/api/files")
class FileController(private val fileService: FileService) {
    
    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): Map<String, String> {
        val fileName = fileService.saveFile(file)
        return mapOf("url" to "/api/files/$fileName")
    }

    @GetMapping("/{fileName}")
    fun getFile(@PathVariable fileName: String): ResponseEntity<Resource> {
        val file = fileService.getFile(fileName)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(Files.probeContentType(file.toPath())))
            .body(FileSystemResource(file))
    }
} 