package com.example.appchat.activity

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.adapter.MessageAdapter
import com.example.appchat.adapter.SearchResultAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.time.LocalDateTime
import android.content.BroadcastReceiver
import android.content.Context
import com.example.appchat.databinding.ActivityChatBinding
import android.content.pm.PackageManager
import android.widget.*
import com.example.appchat.util.FileUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.appchat.R
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.appchat.util.AudioRecorderUtil
import com.example.appchat.util.AudioPlayerUtil
import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import com.example.appchat.util.EncryptionUtil

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var voiceButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var adapter: MessageAdapter
    private var receiverId: Long = 0
    private var receiverName: String = ""
    private var chatType: String = "PRIVATE"
    private var isMultiSelectMode = false
    private var currentChatType: String = "PRIVATE"
    private var currentReceiverId: Long = 0
    private var currentGroupId: Long = -1
    private var title: String = ""
    private var partnerId: Long = 0
    private var currentUserId: Long = 0
    private lateinit var baseUrl: String  // 添加 baseUrl 变量
    
    // 音频相关
    private lateinit var audioPlayer: AudioPlayerUtil
    private var audioRecorder: AudioRecorderUtil? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    // ActivityResultLauncher for file picking
    private val filePickerLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    // 在类定义中添加广播接收器
    private val closeActivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val groupId = intent.getLongExtra("group_id", -1)
            if (groupId == currentGroupId) {
                finish()
            }
        }
    }

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter
    private var currentChatPartnerId: Long = 0
    private var currentChatPartnerName: String = ""
    private var currentChatPartnerAvatar: String? = null
    private var currentUserNickname: String = ""
    private var currentUserAvatar: String? = null
    private var isSearchMode = false
    private var searchQuery = ""
    private var highlightedMessageId: Long = -1L
    private var selectedMessages = mutableSetOf<Long>()
    private var recordingStartTime: Long = 0
    private var recordingFile: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - recordingStartTime
                updateRecordingTime(elapsedTime)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // 所有权限都已授予
            startRecording()
        } else {
            // 有权限被拒绝
            Toast.makeText(this, "需要录音权限才能发送语音消息", Toast.LENGTH_SHORT).show()
        }
    }

    private var recordingTipDialog: AlertDialog? = null
    private var recordingTime = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 baseUrl
        baseUrl = getString(
            R.string.server_url_format,
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )

        // 初始化所有视图组件
        messagesList = binding.messagesList
        messageInput = binding.messageInput
        sendButton = binding.sendButton
        attachButton = binding.attachButton
        voiceButton = binding.voiceButton
        deleteButton = binding.deleteButton

        // 初始化音频工具
        audioRecorder = AudioRecorderUtil(this)
        audioPlayer = AudioPlayerUtil(this)

        // 获取当前用户ID
        currentUserId = UserPreferences.getUserId(this)

        // 从Intent获取聊天参数
        chatType = intent.getStringExtra("chat_type") ?: "PRIVATE"
        receiverId = intent.getLongExtra("receiver_id", -1)
        receiverName = intent.getStringExtra("receiver_name") ?: "未知用户"

        if (receiverId <= 0) {
            Toast.makeText(this, "无效的聊天对象ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化聊天
        setupChat()
        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        setupVoiceButton()
        setupDeleteButton()
        loadChatHistory()
        setupWebSocket()

        // 如果是私聊，加载对方头像
        if (chatType == "PRIVATE") {
            loadPartnerAvatar()
        }

        // 获取需要高亮的消息ID
        val highlightMessageId = intent.getLongExtra("highlight_message_id", -1L)
        if (highlightMessageId != -1L) {
            // 在加载消息后，滚动到该消息并高亮显示
            adapter.setHighlightedMessageId(highlightMessageId)
            // 可以添加一个延迟，确保消息列表已经加载
            Handler(Looper.getMainLooper()).postDelayed({
                val position = adapter.findMessagePosition(highlightMessageId)
                if (position != -1) {
                    messagesList.scrollToPosition(position)
                }
            }, 500)
        }
    }

    private fun setupRecyclerView() {
        messagesList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        adapter = MessageAdapter(
            context = this,
            currentUserId = UserPreferences.getUserId(this),
            currentChatType = chatType,
            chatPartnerId = receiverId,
            onMessageLongClick = { position ->
                if (!isMultiSelectMode) {
                    enterMultiSelectMode()
                    adapter.toggleMessageSelection(position)
                    updateSelectionTitle()
                    true
                } else {
                    false
                }
            },
            onMessageClick = { position ->
                if (isMultiSelectMode) {
                    adapter.toggleMessageSelection(position)
                    updateSelectionTitle()
                }
            },
            onMessageDelete = { messageId ->
                // 处理消息删除
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.apiService.deleteMessage(
                            messageId = messageId,
                            userId = UserPreferences.getUserId(this@ChatActivity)
                        )
                        if (response.isSuccessful) {
                            adapter.removeMessage(messageId)
                        } else {
                            Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        messagesList.adapter = adapter
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun setupAttachButton() {
        attachButton.setOnClickListener {
            showFileChooser()
        }
    }

    private fun showFileChooser() {
        println("Showing file chooser")
        filePickerLauncher.launch("*/*")
    }

    private fun loadChatHistory() {
        val userId = UserPreferences.getUserId(this)

        when (chatType) {
            "GROUP" -> {
                println("📥 Loading group messages for group: $currentGroupId")
                lifecycleScope.launch {
                    try {
                        val messages = ApiClient.apiService.getGroupMessages(currentGroupId, userId)
                        adapter.setMessages(messages)
                        messagesList.scrollToPosition(adapter.itemCount - 1)
                    } catch (e: Exception) {
                        println("❌ Failed to load group messages: ${e.message}")
                        Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                println("📥 Loading private messages with user: $currentReceiverId")
                lifecycleScope.launch {
                    try {
                        val privateMessages = ApiClient.apiService.getPrivateMessages(
                            userId = userId,
                            otherId = currentReceiverId
                        )

                        val mappedMessages = privateMessages.map { message ->
                            message.copy(
                                receiverId = if (message.senderId == userId)
                                    currentReceiverId else userId,
                                chatType = "PRIVATE"
                            )
                        }

                        // 更新UI
                        adapter.setMessages(mappedMessages)
                        messagesList.scrollToPosition(adapter.itemCount - 1)
                    } catch (e: Exception) {
                        println("❌ Failed to load private messages: ${e.message}")
                        Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupWebSocket() {
        val userId = UserPreferences.getUserId(this)
        println("📱 Retrieved userId: $userId")

        // 设置当前聊天
        WebSocketManager.setCurrentChat(
            userId = userId,
            partnerId = when (chatType) {
                "GROUP" -> currentGroupId  // 使用 currentGroupId
                else -> currentReceiverId
            },
            isGroup = (chatType == "GROUP")
        )

        WebSocketManager.addMessageListener { message ->
            // 根据聊天类型判断是否显示消息
            val shouldShowMessage = when (currentChatType) {
                "GROUP" -> message.groupId == currentGroupId  // 群聊消息
                else -> {  // 私聊消息
                    val currentUserId = UserPreferences.getUserId(this)
                    (message.senderId == currentReceiverId && message.receiverId == currentUserId) ||
                    (message.senderId == currentUserId && message.receiverId == currentReceiverId)
                }
            }

            if (shouldShowMessage) {
                runOnUiThread {
                    // 只有收到的消息才添加到UI，发送的消息已经在sendMessage中添加了
                    if (message.senderId != UserPreferences.getUserId(this)) {
                        adapter.addMessage(message)
                        messagesList.scrollToPosition(adapter.itemCount - 1)
                        markSessionAsRead()
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isEmpty()) return

        sendMessage(content, "TEXT")
        messageInput.text.clear()
    }

    private fun showDeleteButton() {
        // 显示删除按钮
        binding.deleteButton.visibility = View.VISIBLE

        // 隐藏其他输入相关控件
        binding.messageInput.visibility = View.GONE
        binding.sendButton.visibility = View.GONE
        binding.attachButton.visibility = View.GONE
    }

    private fun hideDeleteButton() {
        // 隐藏删除按钮
        binding.deleteButton.visibility = View.GONE

        // 显示其他输入相关控件
        binding.messageInput.visibility = View.VISIBLE
        binding.sendButton.visibility = View.VISIBLE
        binding.attachButton.visibility = View.VISIBLE
    }

    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        adapter.enterMultiSelectMode()
        showDeleteButton()  // 显示删除按钮
        supportActionBar?.title = "已选择 0 条消息"
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.exitMultiSelectMode()
        hideDeleteButton()  // 隐藏删除按钮
        supportActionBar?.title = when (currentChatType) {
            "PRIVATE" -> receiverName
            "GROUP" -> "$receiverName (群聊)"
            else -> receiverName
        }

        // 恢复正常的输入界面
        messageInput.visibility = View.VISIBLE
        sendButton.visibility = View.VISIBLE
        attachButton.visibility = View.VISIBLE
    }

    private fun updateSelectionTitle() {
        val selectedCount = adapter.getSelectedMessages().size
        supportActionBar?.title = "已选择 $selectedCount 条消息"
    }

    private fun deleteMessage(messageId: Long) {
        lifecycleScope.launch {
            try {
                // 先从本地缓存中移除
                adapter.removeMessageCompletely(messageId)

                // 然后同步到服务器
                ApiClient.apiService.deleteMessage(
                    messageId = messageId,
                    userId = UserPreferences.getUserId(this@ChatActivity)
                ).let { response ->
                    if (response.isSuccessful) {
                        Toast.makeText(this@ChatActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        // 如果服务器删除失败，恢复本地缓存
                        loadMessages(
                            if (currentChatType == "GROUP") currentGroupId else currentReceiverId,
                            currentChatType
                        )
                        Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // 发生错误时恢复本地缓存
                loadMessages(
                    if (currentChatType == "GROUP") currentGroupId else currentReceiverId,
                    currentChatType
                )
                Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 使用应用私有目录中的临时文件
                val file = FileUtil.getFileFromUri(this@ChatActivity, uri)
                if (file == null) {
                    Toast.makeText(this@ChatActivity, "文件处理失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val response = ApiClient.apiService.uploadFile(body)
                if (response.isSuccessful) {
                    response.body()?.let { fileResponse ->
                        // 发送包含文件URL的消息
                        sendMessage(
                            content = file.name,  // 使用原始文件名
                            type = "FILE",
                            fileUrl = fileResponse.url
                        )
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "上传失败", Toast.LENGTH_SHORT).show()
                }

                // 删除临时文件
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ChatActivity, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isMultiSelectMode) {
                    exitMultiSelectMode()
                } else {
                    finish()
                }
                true
            }
            R.id.action_search -> {
                showSearchMessagesDialog()
                true
            }
            R.id.action_delete_friend -> {
                showDeleteFriendConfirmDialog()
                true
            }
            R.id.action_leave_group -> {
                showLeaveGroupConfirmationDialog()
                true
            }
            R.id.action_group_settings -> {
                openGroupSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteFriendConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除好友")
            .setMessage("确定要删除该好友吗？删除后将清空所有聊天记录。")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    try {
                        ApiClient.apiService.deleteFriend(
                            UserPreferences.getUserId(this@ChatActivity),
                            currentReceiverId
                        )
                        Toast.makeText(this@ChatActivity, "已删除好友", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLeaveGroupConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出群聊")
            .setMessage("确定要退出此群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                leaveGroup()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun leaveGroup() {
        val userId = UserPreferences.getUserId(this)
        val groupId = intent.getLongExtra("group_id", -1)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.leaveGroup(groupId, userId)
                if (response.isSuccessful) {
                    Toast.makeText(this@ChatActivity, "已退出群聊", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ChatActivity, "退出群聊失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.removeMessageListeners()
        WebSocketManager.setCurrentChat(
            userId = UserPreferences.getUserId(this),
            partnerId = 0,
            isGroup = false
        )

        // 取消注册广播接收器
        try {
            unregisterReceiver(closeActivityReceiver)
        } catch (e: IllegalArgumentException) {
            // 忽略未注册的异常
        }

        // 停止录音和播放
        if (isRecording) {
            audioRecorder?.cancelRecording()
        }
        audioPlayer.stopAudio()

        adapter.onDestroy()  // 清理 Handler
    }

    private fun showImagePreview(fileUrl: String?) {
        if (fileUrl != null) {
            val intent = Intent(this, ImagePreviewActivity::class.java)
            intent.putExtra("imageUrl", fileUrl)
            startActivity(intent)
        }
    }

    private fun showVideoPreview(fileUrl: String?) {
        if (fileUrl != null) {
            val intent = Intent(this, VideoPreviewActivity::class.java)
            intent.putExtra("videoUrl", fileUrl)
            startActivity(intent)
        }
    }

    private fun openFile(fileUrl: String?) {
        if (fileUrl != null) {
            // 实现文件下载和打开逻辑
            Toast.makeText(this, "正在打开文件...", Toast.LENGTH_SHORT).show()
            // TODO: 实现文件下载和打开
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 根据聊天类型加载不同的菜单
        val isGroup = chatType.equals("GROUP", ignoreCase = true)

        if (isGroup) {
            // 加载群聊菜单 - 只有搜索和设置按钮
            menuInflater.inflate(R.menu.chat_menu_group, menu)
        } else {
            // 加载私聊菜单 - 有搜索和删除好友选项
            menuInflater.inflate(R.menu.chat_menu_private, menu)
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isGroup = chatType == "GROUP"
        Log.d("ChatActivity", "Preparing menu: chatType=$chatType, isGroup=$isGroup")

        menu.findItem(R.id.action_leave_group)?.isVisible = isGroup
        menu.findItem(R.id.action_group_settings)?.isVisible = isGroup
        menu.findItem(R.id.action_delete_friend)?.isVisible = !isGroup

        return super.onPrepareOptionsMenu(menu)
    }

    private fun showSearchMessagesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search_messages, null)
        val searchInput = dialogView.findViewById<EditText>(R.id.searchInput)
        val resultsList = dialogView.findViewById<RecyclerView>(R.id.searchResults)
        resultsList.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索聊天记录")
            .setView(dialogView)
            .create()

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString()
                searchMessages(query, resultsList, { position ->
                    dialog.dismiss()
                    messagesList.scrollToPosition(position)
                    adapter.highlightMessage(position)
                }, dialog)
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun searchMessages(
        query: String, 
        resultsList: RecyclerView, 
        onItemClick: (Int) -> Unit,
        dialog: AlertDialog
    ) {
        // 在当前消息列表中搜索
        val searchResults = adapter.searchMessages(query)

        // 创建搜索结果适配器
        val adapter = SearchResultAdapter(
            context = this,
            messages = searchResults,
            onItemClick = { position ->
                // 处理点击事件
                val message = searchResults[position].first
                // 滚动到消息位置
                val originalPosition = searchResults[position].second
                // 使用 RecyclerView 的 scrollToPosition 方法
                resultsList.scrollToPosition(originalPosition)
                adapter.highlightMessage(originalPosition)
                // 关闭搜索对话框
                dialog.dismiss()
            }
        )

        resultsList.adapter = adapter
    }

    private fun markSessionAsRead() {
        // 确保使用正确的partnerId
        val partnerId = when (currentChatType) {
            "GROUP" -> currentReceiverId // 对于群聊，使用群ID
            else -> currentReceiverId    // 对于私聊，使用接收者ID
        }

        if (partnerId <= 0) {
            println("⚠️ Skipping markSessionAsRead because partnerId is $partnerId")
            return
        }

        println("📝 Marking messages as read: userId=$currentUserId, partnerId=$partnerId")

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.markSessionAsRead(
                    userId = currentUserId,
                    partnerId = partnerId,  // 使用 partnerId 而不是 sessionId
                    type = currentChatType
                )
                println("✅ markSessionAsRead API response: $response")
            } catch (e: Exception) {
                println("❌ Failed to mark messages as read: ${e.message}")
            }
        }
    }

    private fun setupChat() {
        // 从Intent中获取聊天参数，不要覆盖之前设置的值
        println("📝 setupChat() - Current values:")
        println("   chatType: $chatType")
        println("   receiverId: $receiverId")
        println("   receiverName: $receiverName")

        // 确保聊天类型和接收者ID正确设置
        currentChatType = chatType
        currentReceiverId = receiverId
        partnerId = receiverId

        // 如果是群聊，设置群ID
        if (chatType == "GROUP") {
            currentGroupId = receiverId  // 对于群聊，receiverId 就是群ID
        }

        println("📝 Setup completed - Updated values:")
        println("   currentChatType: $currentChatType")
        println("   currentReceiverId: $currentReceiverId")
        println("   currentGroupId: $currentGroupId")

        // 创建新的适配器实例
        adapter = MessageAdapter(
            context = this,
            currentUserId = UserPreferences.getUserId(this),
            currentChatType = chatType,
            chatPartnerId = receiverId,
            onMessageLongClick = { position ->
                if (!isMultiSelectMode) {
                    enterMultiSelectMode()
                    adapter.toggleMessageSelection(position)
                    updateSelectionTitle()
                    true
                } else {
                    false
                }
            },
            onMessageClick = { position ->
                if (isMultiSelectMode) {
                    adapter.toggleMessageSelection(position)
                    updateSelectionTitle()
                }
            },
            onMessageDelete = { messageId ->
                lifecycleScope.launch {
                    try {
                        ApiClient.apiService.deleteMessage(
                            messageId = messageId,
                            userId = UserPreferences.getUserId(this@ChatActivity)
                        )
                        adapter.removeMessage(messageId)
                        Toast.makeText(this@ChatActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        messagesList.adapter = adapter

        // 设置工具栏标题
        title = when (chatType) {
            "GROUP" -> "$receiverName (群聊)"
            else -> receiverName
        }

        // 设置工具栏
        setupToolbar()

        // 标记会话为已读
        if (currentReceiverId > 0) {
            markSessionAsRead()
        } else {
            println("⚠️ Cannot mark session as read - invalid receiverId: $currentReceiverId")
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)  // 不显示默认标题
        }

        // 设置自定义标题
        binding.toolbarTitle.text = when (chatType) {
            "GROUP" -> "$receiverName (群聊)"
            else -> receiverName
        }

        // 加载头像
        val avatarUrl = when (chatType) {
            "GROUP" -> "$baseUrl/api/groups/$receiverId/avatar"
            else -> "$baseUrl/api/users/$receiverId/avatar"
        }

        Glide.with(this)
            .load(avatarUrl)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .circleCrop()
            .placeholder(
                if (chatType == "GROUP")
                    R.drawable.default_group_avatar
                else
                    R.drawable.default_avatar
            )
            .error(
                if (chatType == "GROUP")
                    R.drawable.default_group_avatar
                else
                    R.drawable.default_avatar
            )
            .into(binding.partnerAvatar)
    }

    private fun updateTitle(newTitle: String) {
        title = newTitle
        setupToolbar()
    }

    private fun loadMessages(partnerId: Long, type: String) {
        lifecycleScope.launch {
            try {
                val messages = when (type) {
                    "GROUP" -> {
                        println("📥 Loading group messages for group: $partnerId")
                        ApiClient.apiService.getGroupMessages(
                            groupId = partnerId,
                            userId = UserPreferences.getUserId(this@ChatActivity)  // 添加 userId 参数
                        )
                    }
                    else -> ApiClient.apiService.getPrivateMessages(
                        userId = UserPreferences.getUserId(this@ChatActivity),
                        otherId = partnerId
                    )
                }
                adapter.setMessages(messages)
                messagesList.scrollToPosition(adapter.itemCount - 1)
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error loading chat history: ${e.message}")
                Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage(content: String, type: String, fileUrl: String? = null) {
        val userId = UserPreferences.getUserId(this)
        val username = UserPreferences.getUsername(this)

        // 如果是文本消息，进行加密
        val processedContent = if (type == "TEXT") {
            val encryptedContent = EncryptionUtil.encrypt(content)
            EncryptionUtil.addEncryptionMark(encryptedContent)
        } else {
            content
        }

        val message = username?.let {
            ChatMessage(
                id = null,
                senderId = userId,
                senderName = it,
                content = processedContent,
                type = MessageType.valueOf(type),
                receiverId = if (chatType == "PRIVATE") partnerId else null,
                receiverName = if (chatType == "PRIVATE") title else null,
                groupId = if (chatType == "GROUP") currentGroupId else null,
                groupName = if (chatType == "GROUP") title else null,
                timestamp = LocalDateTime.now(),
                fileUrl = fileUrl,
                chatType = chatType
            )
        }

        // 发送到服务器
        message?.let {
            WebSocketManager.sendMessage(it,
                onSuccess = {
                    // 消息发送成功后再添加到UI，使用带有 fileUrl 的消息对象
                    runOnUiThread {
                        adapter.addMessage(message)
                        messagesList.scrollToPosition(adapter.itemCount - 1)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "发送失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // 发送广播通知 MessageDisplayFragment 更新会话列表
        val intent = Intent("com.example.appchat.UPDATE_CHAT_SESSIONS")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun showDeleteConfirmDialog(messages: List<ChatMessage>) {
        AlertDialog.Builder(this)
            .setTitle("删除消息")
            .setMessage("确定要删除选中的 ${messages.size} 条消息吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteSelectedMessages(messages)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSelectedMessages(messages: List<ChatMessage>) {
        lifecycleScope.launch {
            try {
                var successCount = 0
                var failCount = 0

                // 逐个删除消息
                for (message in messages) {
                    message.id?.let { messageId ->
                        try {
                            val response = ApiClient.apiService.deleteMessage(
                                messageId = messageId,
                                userId = UserPreferences.getUserId(this@ChatActivity)
                            )
                            if (response.isSuccessful) {
                                successCount++
                                // 从界面上移除消息
                                runOnUiThread {
                                    adapter.removeMessage(messageId)
                                }
                            } else {
                                failCount++
                            }
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                }

                // 显示删除结果
                runOnUiThread {
                    when {
                        failCount == 0 -> {
                            Toast.makeText(this@ChatActivity, "删除成功", Toast.LENGTH_SHORT).show()
                            exitMultiSelectMode()
                        }
                        successCount == 0 -> {
                            Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this@ChatActivity,
                                "成功删除 $successCount 条消息，失败 $failCount 条",
                                Toast.LENGTH_SHORT
                            ).show()
                            exitMultiSelectMode()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDeleteButton() {
        deleteButton = binding.deleteButton
        deleteButton.visibility = View.GONE
        deleteButton.setOnClickListener {
            val selectedMessages = adapter.getSelectedMessages()
            if (selectedMessages.isNotEmpty()) {
                showDeleteConfirmDialog(selectedMessages)
            }
        }
    }

    private fun markMessagesAsRead() {
        val sessionId = intent.getLongExtra("sessionId", -1)
        if (sessionId != -1L) {
            val userId = UserPreferences.getUserId(this)
            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.markMessagesAsRead(sessionId, userId)
                    if (!response.isSuccessful) {
                        Log.e("ChatActivity", "Failed to mark messages as read: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error marking messages as read", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 其他代码...
        markMessagesAsRead()
    }

    private fun openGroupSettings() {
        val groupId = intent.getLongExtra("receiver_id", -1)
        if (groupId == -1L) {
            Toast.makeText(this, "群组ID无效", Toast.LENGTH_SHORT).show()
            return
        }

        val groupName = intent.getStringExtra("chatName") ?: ""

        // 将获取管理员状态的逻辑移到 GroupSettingsActivity
        val intent = Intent(this, GroupSettingsActivity::class.java).apply {
            putExtra("group_id", groupId)
            putExtra("group_name", groupName)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            FileUtil.STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 权限已获取，继续之前的操作
                    // ...
                } else {
                    Toast.makeText(this, "需要存储权限才能上传文件", Toast.LENGTH_SHORT).show()
                }
            }
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 录音权限已获取，开始录音
                    startRecording()
                } else {
                    Toast.makeText(this, "需要录音权限才能发送语音消息", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadPartnerAvatar() {
        val avatarUrl = when (chatType) {
            "GROUP" -> "$baseUrl/api/groups/$receiverId/avatar"
            else -> "$baseUrl/api/users/$receiverId/avatar"
        }

        // 确保头像 ImageView 存在于布局中
        binding.toolbar.findViewById<ImageView>(R.id.partnerAvatar)?.let { avatarView ->
            Glide.with(this)
                .load(avatarUrl)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .circleCrop()
                .placeholder(
                    if (chatType == "GROUP")
                        R.drawable.default_group_avatar
                    else
                        R.drawable.default_avatar
                )
                .error(
                    if (chatType == "GROUP")
                        R.drawable.default_group_avatar
                    else
                        R.drawable.default_avatar
                )
                .into(avatarView)
        }
    }

    fun getCurrentGroupId(): Long = currentGroupId

    fun getCurrentPartnerId(): Long = currentReceiverId

    private fun setupVoiceButton() {
        voiceButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 开始录音
                    if (checkAudioPermission()) {
                        startRecording()
                        // 显示录音提示
                        showRecordingTip()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 检查是否滑动到取消区域
                    val cancelArea = isInCancelArea(event.x, event.y)
                    updateRecordingTip(cancelArea)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 停止录音
                    val cancelArea = isInCancelArea(event.x, event.y)
                    if (cancelArea) {
                        cancelRecording()
                    } else {
                        stopRecording()
                    }
                    // 隐藏录音提示
                    hideRecordingTip()
                    true
                }
                else -> false
            }
        }
    }

    private fun showRecordingTip() {
        // 显示录音提示对话框
        recordingTipDialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_recording_tip)
            .create()
        recordingTipDialog?.show()
        
        // 开始录音动画
        startRecordingAnimation()
    }

    private fun updateRecordingTip(isInCancelArea: Boolean) {
        // 更新录音提示UI
        recordingTipDialog?.findViewById<ImageView>(R.id.recordingIcon)?.setImageResource(
            if (isInCancelArea) R.drawable.ic_cancel else R.drawable.ic_recording
        )
        recordingTipDialog?.findViewById<TextView>(R.id.recordingText)?.text = 
            if (isInCancelArea) "松开手指，取消发送" else "手指上滑，取消发送"
    }

    private fun hideRecordingTip() {
        recordingTipDialog?.dismiss()
        recordingTipDialog = null
        stopRecordingAnimation()
    }

    private fun isInCancelArea(x: Float, y: Float): Boolean {
        // 判断是否滑动到取消区域（上滑超过一定距离）
        return y < voiceButton.top - 100 // 可以根据需要调整距离
    }

    private fun startRecordingAnimation() {
        // 开始录音动画
        handler.post(recordingRunnable)
    }

    private fun stopRecordingAnimation() {
        // 停止录音动画
        handler.removeCallbacks(recordingRunnable)
    }

    private fun updateRecordingTime(elapsedTime: Long) {
        val seconds = (elapsedTime / 1000).toInt()
        recordingTipDialog?.findViewById<TextView>(R.id.recordingTime)?.text = 
            String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun startRecording() {
        try {
            recordingTime = 0
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            audioRecorder = AudioRecorderUtil(this)
            audioRecorder?.setOnErrorListener { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                hideRecordingTip()
            }
            audioRecorder?.startRecording()
            // 开始更新录音时间
            handler.post(recordingRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            hideRecordingTip()
        }
    }

    private fun stopRecording() {
        try {
            isRecording = false
            handler.removeCallbacks(recordingRunnable)
            audioRecorder?.stopRecording()?.let { file ->
                if (file.exists() && file.length() > 0) {
                    uploadAudioFile(file)
                } else {
                    Toast.makeText(this, "录音文件无效", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "停止录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            audioRecorder = null
        }
    }

    private fun cancelRecording() {
        try {
            isRecording = false
            handler.removeCallbacks(recordingRunnable)
            audioRecorder?.cancelRecording()
            Toast.makeText(this, "已取消录音", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "取消录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            audioRecorder = null
        }
    }

    private fun uploadAudioFile(file: File) {
        lifecycleScope.launch {
            try {
                val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val response = ApiClient.apiService.uploadFile(body)
                if (response.isSuccessful) {
                    response.body()?.let { fileResponse ->
                        sendAudioMessage(fileResponse.url)
                    } ?: run {
                        Toast.makeText(this@ChatActivity, "上传失败：服务器返回为空", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "上传失败：${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "上传失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                file.delete()
            }
        }
    }

    private fun sendAudioMessage(audioUrl: String) {
        val message = ChatMessage(
            id = null,
            senderId = currentUserId,
            senderName = UserPreferences.getUsername(this) ?: "",
            content = audioUrl,
            type = MessageType.AUDIO,
            receiverId = if (currentChatType == "PRIVATE") currentReceiverId else null,
            receiverName = if (currentChatType == "PRIVATE") receiverName else null,
            groupId = if (currentChatType == "GROUP") currentGroupId else null,
            groupName = if (currentChatType == "GROUP") title else null,
            timestamp = LocalDateTime.now(),
            fileUrl = audioUrl,
            chatType = currentChatType
        )
        
        // 发送到服务器
        WebSocketManager.sendMessage(message,
            onSuccess = {
                runOnUiThread {
                    adapter.addMessage(message)
                    messagesList.scrollToPosition(adapter.itemCount - 1)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "发送失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun checkAudioPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            false
        }
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1002
    }
}