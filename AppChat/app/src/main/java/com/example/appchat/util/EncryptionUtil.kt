package com.example.appchat.util

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 消息加密工具类
 * 使用AES加密算法对消息内容进行加密和解密
 */
class EncryptionUtil {
    companion object {
        // 加密密钥，实际应用中应该从安全的地方获取，如服务器或安全存储
        private const val SECRET_KEY = "AppChatSecretKey123456789012345678901234"
        
        /**
         * 加密消息内容
         * @param content 原始消息内容
         * @return 加密后的Base64字符串
         */
        fun encrypt(content: String): String {
            try {
                val key = generateKey()
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encryptedBytes = cipher.doFinal(content.toByteArray())
                return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                e.printStackTrace()
                return content // 加密失败时返回原始内容
            }
        }
        
        /**
         * 解密消息内容
         * @param encryptedContent 加密后的Base64字符串
         * @return 解密后的原始消息内容
         */
        fun decrypt(encryptedContent: String): String {
            try {
                val key = generateKey()
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.DECRYPT_MODE, key)
                val decryptedBytes = cipher.doFinal(Base64.decode(encryptedContent, Base64.DEFAULT))
                return String(decryptedBytes)
            } catch (e: Exception) {
                e.printStackTrace()
                return encryptedContent // 解密失败时返回加密内容
            }
        }
        
        /**
         * 生成AES密钥
         * 使用SHA-256对密钥进行哈希，确保密钥长度为16字节
         */
        private fun generateKey(): SecretKeySpec {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(SECRET_KEY.toByteArray())
            val key = ByteArray(16)
            System.arraycopy(bytes, 0, key, 0, key.size)
            return SecretKeySpec(key, "AES")
        }
        
        /**
         * 检查内容是否已加密
         * 简单检查：如果内容以特定前缀开始，则认为已加密
         */
        fun isEncrypted(content: String): Boolean {
            return content.startsWith("ENC:")
        }
        
        /**
         * 添加加密标记
         */
        fun addEncryptionMark(encryptedContent: String): String {
            return "ENC:$encryptedContent"
        }
        
        /**
         * 移除加密标记
         */
        fun removeEncryptionMark(content: String): String {
            return if (content.startsWith("ENC:")) {
                content.substring(4)
            } else {
                content
            }
        }
    }
} 