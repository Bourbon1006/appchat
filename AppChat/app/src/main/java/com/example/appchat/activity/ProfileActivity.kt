package com.example.appchat.activity

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.api.ApiClient
import com.example.appchat.util.UserPreferences
import com.example.appchat.util.FileUtil
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import androidx.lifecycle.lifecycleScope
import com.example.appchat.R
import okhttp3.ResponseBody

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

    private fun uploadAvatar(uri: Uri) {
        val file = FileUtil.getFileFromUri(this, uri) ?: run {
            Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("正在上传头像...")
            setCancelable(false)
            show()
        }

        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("avatar", file.name, requestFile)
        val userId = UserPreferences.getUserId(this)

        // 使用 lifecycleScope 来启动协程
        lifecycleScope.launch {
            try {
                val response: Response<ResponseBody> = ApiClient.apiService.uploadAvatar(userId, body)
                if (response.isSuccessful) {
                    // 重新加载头像
                    loadAvatar()
                    Toast.makeText(this@ProfileActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "未知错误"
                    Toast.makeText(this@ProfileActivity, "上传失败: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }

    private fun loadAvatar() {
        val userId = UserPreferences.getUserId(this)
        val baseUrl = getString(
            R.string.server_url_format,
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )
        val avatarUrl = "$baseUrl/api/users/$userId/avatar"
        
        Glide.with(this)
            .load(avatarUrl)
            .circleCrop()
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(avatarImage)
    }

    override fun onBackPressed() {
        // 发送广播通知 MainActivity 刷新头像
        sendBroadcast(Intent("com.example.appchat.REFRESH_AVATAR"))
        super.onBackPressed()
    }
} 