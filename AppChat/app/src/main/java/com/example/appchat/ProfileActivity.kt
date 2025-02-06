package com.example.appchat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.api.ApiClient
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {
    private lateinit var avatarImage: ImageView
    private val apiService = ApiClient.apiService

    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAvatar(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        avatarImage = findViewById(R.id.avatarImage)
        
        // 加载当前头像
        loadCurrentAvatar()

        // 点击头像更换
        avatarImage.setOnClickListener {
            avatarPickerLauncher.launch("image/*")
        }
    }

    private fun loadCurrentAvatar() {
        val userId = UserPreferences.getUserId(this)
        val avatarUrl = "${getString(R.string.server_url_format).format(
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )}/api/users/$userId/avatar"

        Glide.with(this)
            .load(avatarUrl)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(avatarImage)
    }

    private fun createTempFile(inputStream: java.io.InputStream, prefix: String): File {
        val tempFile = File.createTempFile(prefix, null, cacheDir)
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return tempFile
    }

    private fun uploadAvatar(uri: Uri) {
        val contentResolver = applicationContext.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val file = inputStream?.let { createTempFile(it, "avatar_temp") }

        if (file != null) {
            val mediaType = contentResolver.getType(uri)?.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val body = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestFile)

            val userId = UserPreferences.getUserId(this)
            apiService.uploadAvatar(userId, body).enqueue(object : Callback<UserDTO> {
                override fun onResponse(call: Call<UserDTO>, response: Response<UserDTO>) {
                    if (response.isSuccessful) {
                        println("✅ Avatar uploaded successfully")
                        // 清除Glide缓存
                        Glide.get(this@ProfileActivity).clearMemory()
                        Thread {
                            Glide.get(this@ProfileActivity).clearDiskCache()
                        }.start()
                        
                        // 重新加载头像，添加时间戳避免缓存
                        Handler(Looper.getMainLooper()).post {
                            val avatarUrl = "${getString(R.string.server_url_format).format(
                                getString(R.string.server_ip),
                                getString(R.string.server_port)
                            )}/api/users/$userId/avatar?t=${System.currentTimeMillis()}"
                            
                            Glide.with(this@ProfileActivity)
                                .load(avatarUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .skipMemoryCache(true)  // 跳过内存缓存
                                .diskCacheStrategy(DiskCacheStrategy.NONE)  // 跳过磁盘缓存
                                .placeholder(R.drawable.default_avatar)
                                .error(R.drawable.default_avatar)
                                .into(avatarImage)
                                
                            Toast.makeText(this@ProfileActivity, "头像更新成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        println("❌ Avatar upload failed: ${response.code()}")
                        Toast.makeText(this@ProfileActivity, "头像更新失败", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<UserDTO>, t: Throwable) {
                    println("❌ Network error: ${t.message}")
                    Toast.makeText(this@ProfileActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onBackPressed() {
        // 发送广播通知 MainActivity 刷新头像
        sendBroadcast(Intent("com.example.appchat.REFRESH_AVATAR"))
        super.onBackPressed()
    }
} 