package org.example.appchathandler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.context.annotation.ComponentScan
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.Color

@SpringBootApplication
@EnableJpaRepositories(basePackages = ["org.example.appchathandler.repository"])
@EntityScan(basePackages = ["org.example.appchathandler.entity"])
class AppchatHandlerApplication {
    @Value("\${file.upload.dir}")
    private lateinit var uploadDir: String

    @Value("\${avatar.upload.dir:avatars}")
    private lateinit var avatarDir: String

    @PostConstruct
    fun init() {
        val uploadDirectory = File(System.getProperty("user.dir"), uploadDir)
        val avatarDirectory = File(System.getProperty("user.dir"), avatarDir)

        listOf(uploadDirectory, avatarDirectory).forEach { dir ->
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw RuntimeException("Failed to create directory: ${dir.absolutePath}")
                }
            }
            dir.setReadable(true, false)
            dir.setWritable(true, false)
            println("Directory initialized at: ${dir.absolutePath}")
        }

        // 复制默认头像到头像目录
        val defaultAvatar = File(avatarDirectory, "default.jpg")
        if (!defaultAvatar.exists()) {
            try {
                // 创建一个简单的默认头像
                val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
                val g2d = image.createGraphics()
                
                // 设置背景色
                g2d.color = Color(200, 200, 200)  // 浅灰色
                g2d.fillRect(0, 0, 200, 200)
                
                // 画一个简单的头像轮廓
                g2d.color = Color(150, 150, 150)  // 深灰色
                g2d.fillOval(50, 50, 100, 100)
                
                g2d.dispose()
                
                // 保存为jpg文件
                ImageIO.write(image, "jpg", defaultAvatar)
                defaultAvatar.setReadable(true, false)
                println("✅ Default avatar created at: ${defaultAvatar.absolutePath}")
            } catch (e: Exception) {
                println("❌ Failed to create default avatar: ${e.message}")
                e.printStackTrace()
            }
        }
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
