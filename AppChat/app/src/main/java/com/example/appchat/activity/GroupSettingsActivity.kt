package com.example.appchat.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.appchat.adapter.GroupMemberAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.ActivityGroupSettingsBinding
import com.example.appchat.model.Group
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.activity.OnBackPressedCallback
import com.example.appchat.util.FileUtil
import android.app.ProgressDialog
import com.example.appchat.R
import com.example.appchat.util.loadAvatar

class GroupSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupSettingsBinding
    private var groupId: Long = -1
    private var groupName: String = ""
    private var isGroupAdmin: Boolean = false
    private var avatarUri: Uri? = null
    private var members: List<UserDTO> = emptyList()
    
    // 添加一个常量作为请求码
    companion object {
        private const val REQUEST_ADD_MEMBERS = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        groupId = intent.getLongExtra("group_id", -1)
        groupName = intent.getStringExtra("group_name") ?: ""
        isGroupAdmin = intent.getBooleanExtra("is_admin", false)

        println("11111$groupId")
        setupToolbar()
        loadGroupInfo()
        setupListeners()
        
        // 正确设置返回键处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 执行任何必要的清理操作
                
                // 然后关闭活动，回到群聊界面
                finish()
            }
        })
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "群设置"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    private fun loadGroupInfo() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getGroupDetails(groupId)
                if (response.isSuccessful) {
                    response.body()?.let { group ->
                        // 保存群名称
                        groupName = group.name
                        
                        // 判断当前用户是否为群主
                        val currentUserId = UserPreferences.getUserId(this@GroupSettingsActivity)
                        isGroupAdmin = group.creator.id == currentUserId
                        
                        // 更新UI
                        updateUI(group)
                        
                        // 加载群成员
                        loadGroupMembers()
                    }
                } else {
                    Toast.makeText(this@GroupSettingsActivity, "加载群信息失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateUI(group: Group) {
        binding.groupNameText.text = group.name
        
        // 加载群头像
        loadGroupAvatar()
        
        // 设置管理员权限的UI可见性
        binding.editGroupNameButton.isVisible = isGroupAdmin
        binding.changeAvatarButton.isVisible = isGroupAdmin
        
        // 加载群成员
        loadGroupMembers()
    }
    
    private fun setupListeners() {
        // 更改群名称
        binding.editGroupNameButton.setOnClickListener {
            showChangeGroupNameDialog()
        }
        
        // 更改群头像
        binding.changeAvatarButton.setOnClickListener {
            selectImageFromGallery()
        }
    }
    
    private fun showChangeGroupNameDialog() {
        val editText = EditText(this)
        editText.setText(groupName)
        editText.setPadding(50, 30, 50, 30)
        
        AlertDialog.Builder(this)
            .setTitle("更改群名称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateGroupName(newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateGroupName(newName: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updateGroupName(groupId, newName)
                if (response.isSuccessful) {
                    groupName = newName
                    binding.groupNameText.text = newName
                    Toast.makeText(this@GroupSettingsActivity, "群名称已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@GroupSettingsActivity, "更新群名称失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }
    
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                avatarUri = uri
                Glide.with(this).load(uri).circleCrop().into(binding.groupAvatar)
                uploadGroupAvatar(uri)
            }
        }
    }
    
    private fun loadGroupAvatar() {
        val baseUrl = getString(
            R.string.server_url_format,
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )
        val avatarUrl = "$baseUrl/api/groups/$groupId/avatar"
        
        // 使用扩展函数加载头像
        binding.groupAvatar.loadAvatar(avatarUrl)
    }
    
    private fun uploadGroupAvatar(uri: Uri) {
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

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.uploadGroupAvatar(groupId, body)
                if (response.isSuccessful) {
                    // 重新加载头像
                    loadGroupAvatar()
                    Toast.makeText(this@GroupSettingsActivity, "群头像上传成功", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "未知错误"
                    Toast.makeText(this@GroupSettingsActivity, "上传失败: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }
    
    private fun loadGroupMembers() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getGroupMembers(groupId)
                if (response.isSuccessful) {
                    response.body()?.let { groupMembers ->
                        // 保存到成员变量
                        members = groupMembers
                        setupMembersRecyclerView(groupMembers)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "加载群成员失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupMembersRecyclerView(members: List<UserDTO>) {
        val adapter = GroupMemberAdapter(members, isGroupAdmin) { user, action ->
            if (action == "REMOVE") {
                showRemoveMemberConfirmation(user)
            }
        }
        binding.membersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.membersRecyclerView.adapter = adapter
    }
    
    private fun showRemoveMemberConfirmation(user: UserDTO) {
        AlertDialog.Builder(this)
            .setTitle("移除成员")
            .setMessage("确定要将 ${user.username} 移出群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                removeMember(user.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun removeMember(userId: Long) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.removeGroupMember(
                    groupId = groupId,
                    memberId = userId
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@GroupSettingsActivity, "成员已移除", Toast.LENGTH_SHORT).show()
                    loadGroupMembers() // 重新加载成员列表
                } else {
                    Toast.makeText(this@GroupSettingsActivity, "移除成员失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showLeaveGroupConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出群聊")
            .setMessage("确定要退出该群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                leaveGroup()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun leaveGroup() {
        val userId = UserPreferences.getUserId(this)
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.leaveGroup(groupId, userId)
                if (response.isSuccessful) {
                    Toast.makeText(this@GroupSettingsActivity, "已退出群聊", Toast.LENGTH_SHORT).show()
                    // 返回到上一个界面
                    finish()
                    // 如果前一个界面是聊天界面，需要关闭它
                    // 发送广播通知相关活动关闭
                    val intent = Intent("com.example.appchat.CLOSE_CHAT")
                    intent.putExtra("group_id", groupId)
                    sendBroadcast(intent)
                } else {
                    Toast.makeText(this@GroupSettingsActivity, "退出群聊失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupSettingsActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 处理工具栏返回按钮点击
                onBackPressed()
                true
            }
            R.id.action_add_members -> {
                // 处理添加成员菜单项
                if (isGroupAdmin) {
                    val intent = Intent(this, SelectContactsActivity::class.java)
                    intent.putExtra("group_id", groupId)
                    
                    // 获取现有成员ID列表
                    val memberIds = members.map { it.id }.toLongArray()
                    intent.putExtra("existing_members", memberIds)
                    
                    startActivityForResult(intent, REQUEST_ADD_MEMBERS)
                } else {
                    Toast.makeText(this, "只有群主可以添加成员", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_leave_group -> {
                // 处理退出群聊菜单项
                showLeaveGroupConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 只需调用默认实现，它会关闭当前activity并返回到前一个activity（群聊）
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.group_settings_menu, menu)
        return true
    }

    // 添加处理结果的方法
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_MEMBERS && resultCode == Activity.RESULT_OK) {
            // 重新加载群成员列表
            loadGroupMembers()
        }
    }
} 