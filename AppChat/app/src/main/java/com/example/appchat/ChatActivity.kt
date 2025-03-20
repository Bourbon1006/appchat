package com.example.appchat

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.adapter.MessageAdapter
import com.example.appchat.adapter.SearchResultAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.FileDTO
import com.example.appchat.model.MessageType
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.LocalDateTime
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Call
import org.greenrobot.eventbus.EventBus
import com.example.appchat.util.UserManager
import com.example.appchat.api.RetrofitClient

class ChatActivity : AppCompatActivity() {
    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var adapter: MessageAdapter
    private var receiverId: Long = 0
    private var receiverName: String = ""
    private var chatType: String = "private"
    private var isMultiSelectMode = false
    private lateinit var deleteButton: ImageButton
    private var currentChatType: String = "PRIVATE"
    private var currentReceiverId: Long = 0
    private var currentGroupId: Long = -1
    private var title: String = ""
    private var partnerId: Long = -1

    // ActivityResultLauncher for file picking
    private val filePickerLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 初始化基础视图
        initViews()
        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        setupToolbar()
        setupDeleteButton()
        
        // 设置聊天
        setupChat()
        
        // 加载历史消息并设置 WebSocket
        loadChatHistory()
        setupWebSocket()

        // 注册返回键回调
        onBackPressedDispatcher.addCallback(this) {
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            } else {
                finish()
            }
        }
    }

    private fun initViews() {
        messagesList = findViewById(R.id.messagesList)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        deleteButton = findViewById(R.id.deleteButton)
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

    private fun updateSelectedCount(count: Int) {
        if (isMultiSelectMode) {
            supportActionBar?.title = "已选择 $count 条消息"
            if (count == 0) {
                exitMultiSelectMode()
            }
        }
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
        lifecycleScope.launch {
            try {
                val messages = when (currentChatType) {
                    "GROUP" -> {
                        println("📥 Loading group messages for group: $currentGroupId")
                        val groupMessages = ApiClient.apiService.getGroupMessages(
                            groupId = currentGroupId,
                            userId = UserPreferences.getUserId(this@ChatActivity)  // 添加 userId 参数
                        )
                        groupMessages.map { message ->
                            message.copy(
                                groupId = currentGroupId,
                                receiverId = null,
                                receiverName = null,
                                chatType = "GROUP"
                            )
                        }
                    }
                    else -> {
                        println("📥 Loading private messages with user: $currentReceiverId")
                        val currentUserId = UserPreferences.getUserId(this@ChatActivity)
                        val privateMessages = ApiClient.apiService.getPrivateMessages(
                            userId = currentUserId,
                            otherId = currentReceiverId
                        )
                        privateMessages.map { message ->
                            message.copy(
                                receiverId = if (message.senderId == currentUserId) 
                                    currentReceiverId else currentUserId,
                                chatType = "PRIVATE"
                            )
                        }
                    }
                }
                
                // 更新UI
                adapter.setMessages(messages)
                messagesList.scrollToPosition(adapter.itemCount - 1)
                
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error loading chat history: ${e.message}")
                Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebSocket() {
        // 设置当前聊天的用户ID
        WebSocketManager.setCurrentChat(
            userId = UserPreferences.getUserId(this),
            partnerId = if (currentChatType == "GROUP") currentGroupId else currentReceiverId,
            isGroup = currentChatType == "GROUP"
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
        deleteButton.visibility = View.VISIBLE
        messageInput.visibility = View.GONE
        sendButton.visibility = View.GONE
        attachButton.visibility = View.GONE
    }

    private fun hideDeleteButton() {
        deleteButton.visibility = View.GONE
    }

    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        adapter.enterMultiSelectMode()
        showDeleteButton()
        supportActionBar?.title = "已选择 0 条消息"
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.exitMultiSelectMode()
        hideDeleteButton()
        
        // 恢复标题
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
        val file = File(getRealPathFromUri(uri))
        val requestFile = file.asRequestBody("*/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.apiService.uploadFile(body)
                if (response.isSuccessful) {
                    response.body()?.let { fileResponse ->
                        // 发送文件消息
                        val message = UserPreferences.getUsername(this@ChatActivity)?.let {
                            ChatMessage(
                                id = null,
                                senderId = UserPreferences.getUserId(this@ChatActivity),
                                senderName = it,
                                content = fileResponse.url,
                                type = MessageType.FILE,
                                receiverId = if (currentChatType == "PRIVATE") currentReceiverId else null,
                                receiverName = if (currentChatType == "PRIVATE") title else null,
                                groupId = if (currentChatType == "GROUP") currentGroupId else null,
                                timestamp = LocalDateTime.now(),
                                fileUrl = fileResponse.url,
                                chatType = currentChatType
                            )
                        }

                        // 使用 WebSocket 发送消息
                        withContext(Dispatchers.Main) {
                            message?.let {
                                WebSocketManager.sendMessage(it,
                                    onSuccess = {
                                        adapter.addMessage(message)
                                        messagesList.scrollToPosition(adapter.itemCount - 1)
                                    },
                                    onError = { error ->
                                        Toast.makeText(this@ChatActivity, "发送失败: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "文件上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "文件上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String {
        val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        val columnIndex = cursor?.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
        cursor?.moveToFirst()
        val path = cursor?.getString(columnIndex ?: 0) ?: ""
        cursor?.close()
        return path
    }

    private fun isImageFile(extension: String): Boolean {
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp")
    }

    private fun isVideoFile(extension: String): Boolean {
        return extension in listOf("mp4", "avi", "mov", "wmv")
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


    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.removeMessageListeners()
        WebSocketManager.setCurrentChat(
            userId = UserPreferences.getUserId(this),
            partnerId = 0,
            isGroup = false
        )
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
        menuInflater.inflate(R.menu.chat_menu, menu)
        // 只在私聊时显示删除好友选项
        menu.findItem(R.id.action_delete_friend)?.isVisible = currentChatType == "PRIVATE"
        return true
    }

    private fun showSearchMessagesDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索聊天记录")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_messages, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val resultsList = view.findViewById<RecyclerView>(R.id.searchResults)
        resultsList.layoutManager = LinearLayoutManager(this)

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString()
                searchMessages(query, resultsList) { position ->
                    dialog.dismiss()
                    messagesList.scrollToPosition(position)
                    adapter.highlightMessage(position)
                }
                true
            } else {
                false
            }
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun searchMessages(query: String, resultsList: RecyclerView, onItemClick: (Int) -> Unit) {
        // 在当前消息列表中搜索
        val searchResults = adapter.searchMessages(query)
        
        // 创建搜索结果适配器
        val adapter = SearchResultAdapter(searchResults, onItemClick)
        
        resultsList.adapter = adapter
    }

    private fun markSessionAsRead() {
        lifecycleScope.launch {
            try {
                val partnerId = when (currentChatType) {
                    "GROUP" -> currentGroupId
                    else -> currentReceiverId
                }
                println("🔍 Marking session as read - Type: $currentChatType, PartnerId: $partnerId")
                
                ApiClient.apiService.markSessionAsRead(
                    userId = UserPreferences.getUserId(this@ChatActivity),
                    partnerId = partnerId,
                    type = currentChatType
                ).let { response ->
                    if (!response.isSuccessful) {
                        println("⚠️ Failed to mark session as read: ${response.code()}")
                    } else {
                        println("✅ Successfully marked session as read")
                    }
                }
            } catch (e: Exception) {
                println("❌ Error marking session as read: ${e.message}")
            }
        }
    }

    private fun setupGroupChat(groupId: Long, groupName: String) {
        currentChatType = "GROUP"
        currentGroupId = groupId
        receiverName = groupName
        println("🔄 Setting up group chat - GroupID: $groupId, Name: $groupName, title: $receiverName")
        
        // 设置标题
        supportActionBar?.title = "$groupName (群聊)"
        
        // 加载群聊消息
        loadMessages(groupId, "GROUP")
        
        // 标记消息为已读
        markMessagesAsRead(groupId, "GROUP")
    }

    private fun setupPrivateChat(receiverId: Long, receiverName: String) {
        currentChatType = "PRIVATE"
        currentReceiverId = receiverId
        this.receiverName = receiverName
        
        // 更新适配器
        adapter = MessageAdapter(
            context = this,
            currentUserId = UserPreferences.getUserId(this),
            currentChatType = currentChatType,
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
        
        // 加载消息历史
        loadMessages(receiverId, currentChatType)
        
        // 标记消息为已读
        markMessagesAsRead(receiverId, currentChatType)
        
        // 更新工具栏标题
        supportActionBar?.title = receiverName
        
        // 确保 WebSocket 已连接
        if (!WebSocketManager.isConnected()) {
            val serverUrl = "http://192.168.31.194:8080/ws"
            WebSocketManager.init(
                serverUrl = serverUrl,
                userId = UserPreferences.getUserId(this)
            )
        }
    }

    private fun updateToolbarTitle(newTitle: String) {
        supportActionBar?.title = newTitle
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

    private fun markMessagesAsRead(partnerId: Long, type: String) {
        lifecycleScope.launch {
            try {
                // 使用统一的端点
                ApiClient.apiService.markSessionAsRead(
                    userId = UserPreferences.getUserId(this@ChatActivity),
                    partnerId = partnerId,
                    type = type
                )
            } catch (e: Exception) {
                println("❌ Error marking messages as read: ${e.message}")
            }
        }
    }

    private fun sendMessage(content: String, type: String = "TEXT", fileUrl: String? = null) {
        val userId = UserPreferences.getUserId(this)
        val username = UserPreferences.getUsername(this)
        
        val message = username?.let {
            ChatMessage(
                id = null,
                senderId = userId,
                senderName = it,
                content = content,
                type = MessageType.valueOf(type),
                receiverId = if (currentChatType == "PRIVATE") currentReceiverId else null,
                receiverName = if (currentChatType == "PRIVATE") title else null,
                groupId = if (currentChatType == "GROUP") currentGroupId else null,
                timestamp = LocalDateTime.now(),
                fileUrl = fileUrl,  // 确保设置文件 URL
                chatType = currentChatType
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

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            // 设置初始标题
            title = when (currentChatType) {
                "PRIVATE" -> receiverName
                "GROUP" -> "$receiverName (群聊)"
                else -> receiverName
            }
        }
    }

    private fun setupChat() {
        currentChatType = intent.getStringExtra("chat_type") ?: "PRIVATE"
        
        when (currentChatType) {
            "GROUP" -> {
                val groupId = intent.getLongExtra("group_id", -1)
                val groupName = intent.getStringExtra("group_name") ?: ""
                if (groupId != -1L) {
                    currentGroupId = groupId
                    receiverName = groupName
                    supportActionBar?.title = "$groupName (群聊)"  // 使用 supportActionBar 设置标题
                    setupGroupChat(groupId, groupName)
                } else {
                    handleInvalidChat()
                }
            }
            "PRIVATE" -> {
                val receiverId = intent.getLongExtra("receiver_id", -1)
                val receiverName = intent.getStringExtra("receiver_name") ?: ""
                if (receiverId != -1L) {
                    currentReceiverId = receiverId
                    this.receiverName = receiverName
                    supportActionBar?.title = receiverName  // 使用 supportActionBar 设置标题
                    setupPrivateChat(receiverId, receiverName)
                } else {
                    handleInvalidChat()
                }
            }
        }
    }

    private fun handleInvalidChat() {
        Toast.makeText(this, "无法获取聊天信息", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onPause() {
        super.onPause()
        // 退出聊天界面时更新会话列表
        updateMessageSessions()
    }

    private fun updateMessageSessions() {
        val userId = UserManager.getCurrentUser()?.id ?: return
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.messageService.getMessageSessions(userId)
                if (response.isSuccessful) {
                    // 会话列表已更新，通知 MainActivity 刷新
                    EventBus.getDefault().post(SessionUpdateEvent())
                } else {
                    Log.e("ChatActivity", "Failed to update sessions: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error updating sessions", e)
            }
        }
    }

    // 添加 SessionUpdateEvent 类
    class SessionUpdateEvent

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
        deleteButton = findViewById(R.id.deleteButton)
        deleteButton.visibility = View.GONE
        deleteButton.setOnClickListener {
            val selectedMessages = adapter.getSelectedMessages()
            if (selectedMessages.isNotEmpty()) {
                showDeleteConfirmDialog(selectedMessages)
            }
        }
    }
}