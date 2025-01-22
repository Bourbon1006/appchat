package org.example.appchathandler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.multipart.support.StandardServletMultipartResolver
import com.fasterxml.jackson.databind.SerializationFeature
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import java.io.File

@SpringBootApplication
class AppchatHandlerApplication {
    @Value("\${file.upload.dir}")
    private lateinit var uploadDir: String

    @PostConstruct
    fun init() {
        val uploadDirectory = File(System.getProperty("user.dir"), uploadDir)
        if (!uploadDirectory.exists()) {
            if (!uploadDirectory.mkdirs()) {
                throw RuntimeException("Failed to create upload directory: ${uploadDirectory.absolutePath}")
            }
        }
        println("Upload directory initialized at: ${uploadDirectory.absolutePath}")
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    }

    @Bean
    fun multipartResolver(): MultipartResolver {
        return StandardServletMultipartResolver()
    }
}

fun main(args: Array<String>) {
    runApplication<AppchatHandlerApplication>(*args)
}
