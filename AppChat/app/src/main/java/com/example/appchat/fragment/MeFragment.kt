package com.example.appchat.fragment

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.appchat.R
import com.example.appchat.api.ApiClient
import com.example.appchat.api.UpdateNicknameRequest
import com.example.appchat.api.UpdatePasswordRequest
import com.example.appchat.databinding.FragmentMeBinding
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import com.example.appchat.util.loadAvatar
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MeFragment : Fragment() {
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!
    private val userId: Long by lazy { UserPreferences.getUserId(requireContext()) }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        loadUserInfo()
    }

    private fun setupViews() {
        // 设置在线状态下拉框
        val statusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("离线", "在线", "忙碌")
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.statusSpinner.adapter = statusAdapter
        
        // 设置头像点击事件
        binding.avatarContainer.setOnClickListener {
            pickImage()
        }
        
        // 设置修改昵称点击事件
        binding.changeNicknameButton.setOnClickListener {
            showChangeNicknameDialog()
        }
        
        // 设置修改密码点击事件
        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }
        
        // 设置在线状态变化监听
        binding.statusSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateOnlineStatus(position)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadUserInfo() {
        ApiClient.apiService.getUser(userId).enqueue(object : retrofit2.Callback<UserDTO> {
            override fun onResponse(call: retrofit2.Call<UserDTO>, response: retrofit2.Response<UserDTO>) {
                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        // 添加日志
                        Log.d("MeFragment", "原始 avatarUrl: ${user.avatarUrl}")
                        
                        // 处理相对路径的 avatarUrl
                        val fullAvatarUrl = if (user.avatarUrl?.startsWith("/") == true) {
                            val url = ApiClient.BASE_URL.removeSuffix("/") + user.avatarUrl
                            Log.d("MeFragment", "处理后的完整 URL: $url")
                            url
                        } else {
                            Log.d("MeFragment", "使用原始 URL: ${user.avatarUrl}")
                            user.avatarUrl
                        }
                        
                        // 添加更多日志
                        Log.d("MeFragment", "最终使用的 URL: $fullAvatarUrl")
                        
                        binding.avatarImage.loadAvatar(fullAvatarUrl)
                        updateUI(user)
                        binding.statusSpinner.setSelection(user.onlineStatus)
                    }
                } else {
                    // 添加错误日志
                    Log.e("MeFragment", "加载用户信息失败: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, "加载用户信息失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<UserDTO>, t: Throwable) {
                // 添加错误日志
                Log.e("MeFragment", "网络错误: ${t.message}", t)
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(user: UserDTO) {
        binding.nicknameText.text = user.nickname ?: user.username
        // 其他代码...
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun showChangeNicknameDialog() {
        // 创建一个 EditText 用于输入新昵称
        val editText = EditText(requireContext()).apply {
            hint = "请输入新昵称"
            // 获取当前昵称
            setText(binding.nicknameText.text)
            // 设置光标位置到文本末尾
            setSelection(text.length)
        }

        // 创建对话框
        AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val newNickname = editText.text.toString().trim()
                if (newNickname.isNotEmpty()) {
                    updateNickname(newNickname)
                } else {
                    Toast.makeText(context, "昵称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateNickname(nickname: String) {
        // 显示加载指示器
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("正在更新昵称...")
            setCancelable(false)
            show()
        }

        // 创建请求对象
        val request = UpdateNicknameRequest(nickname)

        // 发送请求
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updateNickname(userId, request)
                // 隐藏加载指示器
                progressDialog.dismiss()

                if (response.isSuccessful) {
                    // 更新 UI
                    binding.nicknameText.text = nickname
                    // 更新缓存
                    UserPreferences.saveUserNickname(requireContext(), nickname)
                    Toast.makeText(context, "昵称更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    // 显示错误信息
                    Log.e("MeFragment", "更新昵称失败: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, "更新昵称失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // 隐藏加载指示器
                progressDialog.dismiss()
                // 显示错误信息
                Log.e("MeFragment", "更新昵称异常: ${e.message}", e)
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangePasswordDialog() {
        // 创建一个包含两个输入框的布局
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val oldPasswordEditText = dialogView.findViewById<EditText>(R.id.oldPasswordEditText)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.newPasswordEditText)

        // 创建对话框
        AlertDialog.Builder(requireContext())
            .setTitle("修改密码")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val oldPassword = oldPasswordEditText.text.toString().trim()
                val newPassword = newPasswordEditText.text.toString().trim()
                
                // 验证输入
                when {
                    oldPassword.isEmpty() -> {
                        Toast.makeText(context, "请输入原密码", Toast.LENGTH_SHORT).show()
                    }
                    newPassword.isEmpty() -> {
                        Toast.makeText(context, "请输入新密码", Toast.LENGTH_SHORT).show()
                    }
                    newPassword.length < 6 -> {
                        Toast.makeText(context, "新密码长度不能少于6位", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        updatePassword(oldPassword, newPassword)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updatePassword(oldPassword: String, newPassword: String) {
        // 显示加载指示器
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("正在更新密码...")
            setCancelable(false)
            show()
        }

        // 创建请求对象
        val request = UpdatePasswordRequest(oldPassword, newPassword)

        // 发送请求
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updatePassword(userId, request)
                // 隐藏加载指示器
                progressDialog.dismiss()

                if (response.isSuccessful) {
                    // 更新成功
                    Toast.makeText(context, "密码更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    // 显示错误信息
                    val errorMessage = when (response.code()) {
                        401 -> "原密码错误"
                        else -> "更新密码失败: ${response.code()}"
                    }
                    Log.e("MeFragment", "更新密码失败: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // 隐藏加载指示器
                progressDialog.dismiss()
                // 显示错误信息
                Log.e("MeFragment", "更新密码异常: ${e.message}", e)
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOnlineStatus(status: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updateOnlineStatus(userId, status)
                if (!response.isSuccessful) {
                    Toast.makeText(context, "更新在线状态失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                uploadAvatar(uri)
            }
        }
    }

    private fun uploadAvatar(uri: Uri) {
        // 实现头像上传逻辑
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }
} 