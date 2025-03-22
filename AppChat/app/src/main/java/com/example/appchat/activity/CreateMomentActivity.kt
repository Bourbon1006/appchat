package com.example.appchat.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.appchat.api.ApiClient
import com.example.appchat.api.CreateMomentRequest
import com.example.appchat.databinding.ActivityCreateMomentBinding
import com.example.appchat.util.UserPreferences
import com.example.appchat.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import retrofit2.Response

class CreateMomentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateMomentBinding
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateMomentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnAddImage.setOnClickListener {
            openImagePicker()
        }

        binding.btnPublish.setOnClickListener {
            publishMoment()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICK_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedImageUri = uri
                binding.ivPreview.visibility = android.view.View.VISIBLE
                Glide.with(this).load(uri).into(binding.ivPreview)
            }
        }
    }

    private fun publishMoment() {
        val content = binding.etContent.text.toString()
        if (content.isBlank()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPublish.isEnabled = false
        val userId = UserPreferences.getUserId(this)

        lifecycleScope.launch {
            try {
                var imageUrl: String? = null
                
                // 如果选择了图片，先上传图片
                selectedImageUri?.let { uri ->
                    // 使用 FileUtil 处理文件
                    val file = FileUtil.getFileFromUri(this@CreateMomentActivity, uri)
                    if (file == null) {
                        Toast.makeText(this@CreateMomentActivity, "文件处理失败", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    
                    val response = ApiClient.apiService.uploadFile(filePart)
                    if (response.isSuccessful) {
                        response.body()?.let { fileResponse ->
                            imageUrl = fileResponse.url
                        }
                    } else {
                        Toast.makeText(this@CreateMomentActivity, "图片上传失败", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // 删除临时文件
                    file.delete()
                }

                // 发布动态
                val request = CreateMomentRequest(content, imageUrl)
                val response = ApiClient.apiService.createMoment(userId, request)
                
                if (response.isSuccessful) {
                    Toast.makeText(this@CreateMomentActivity, "发布成功", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@CreateMomentActivity, "发布失败", Toast.LENGTH_SHORT).show()
                    binding.btnPublish.isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.btnPublish.isEnabled = true
                Toast.makeText(this@CreateMomentActivity, "发布失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val IMAGE_PICK_REQUEST = 1
    }
} 