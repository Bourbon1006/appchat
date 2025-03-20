package com.example.appchat.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.appchat.api.ApiClient
import com.example.appchat.api.CreateMomentRequest
import com.example.appchat.databinding.ActivityCreateMomentBinding
import com.example.appchat.util.UserPreferences
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var imageUrl: String? = null
                
                // 如果选择了图片，先上传图片
                selectedImageUri?.let { uri ->
                    val file = File(getRealPathFromUri(uri))
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    
                    val response = ApiClient.apiService.uploadFile(filePart)
                    imageUrl = response.body()?.url
                }

                // 发布动态
                val request = CreateMomentRequest(content, imageUrl)
                ApiClient.apiService.createMoment(userId, request)
                
                runOnUiThread {
                    Toast.makeText(this@CreateMomentActivity, "发布成功", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.btnPublish.isEnabled = true
                    Toast.makeText(this@CreateMomentActivity, "发布失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor?.moveToFirst()
        val path = cursor?.getString(columnIndex ?: 0) ?: ""
        cursor?.close()
        return path
    }

    companion object {
        private const val IMAGE_PICK_REQUEST = 1
    }
} 