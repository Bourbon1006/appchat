package com.example.appchat

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.InputStream
import java.time.LocalDateTime
import retrofit2.Callback
import retrofit2.Response

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

    // ActivityResultLauncher for file picking
    private val filePickerLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 设置 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)  // 禁用 ActionBar 的标题显示
        
        // 获取标题文本视图
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)

        // 获取聊天类型和对方信息
        currentChatType = intent.getStringExtra("chat_type") ?: "PRIVATE"
        
        when (currentChatType) {
            "GROUP" -> {
                currentGroupId = intent.getLongExtra("group_id", -1)
                title = intent.getStringExtra("group_name") ?: ""
                setupGroupChat(currentGroupId, title)
            }
            else -> {
                currentReceiverId = intent.getLongExtra("receiver_id", -1)
                title = intent.getStringExtra("receiver_name") ?: ""
                setupPrivateChat(currentReceiverId, title)
            }
        }

        // 设置标题
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 显示返回按钮
        toolbarTitle.text = when (currentChatType) {
            "GROUP" -> "$title (群聊)"
            else -> title
        }

        initViews()
        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        loadChatHistory()
        setupWebSocket()

        // 标记会话为已读
        markSessionAsRead()
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
            onMessageDelete = { messageId ->
                if (isMultiSelectMode) {
                    adapter.toggleMessageSelection(messageId)
                    updateSelectedCount(adapter.getSelectedMessages().size)
                } else {
                    // 处理消息点击事件
                    adapter.getMessage(messageId)?.let { message ->
                        when (message.type) {
                            MessageType.FILE -> {
                                val extension = message.content.substringAfterLast('.', "").lowercase()
                                when {
                                    isImageFile(extension) -> showImagePreview(message.fileUrl)
                                    isVideoFile(extension) -> showVideoPreview(message.fileUrl)
                                    else -> openFile(message.fileUrl)
                                }
                            }
                            else -> {} // 对于其他类型的消息不做特殊处理
                        }
                    }
                }
            }
        )
        messagesList.adapter = adapter

        adapter.setOnItemLongClickListener { position ->
            val messageId = adapter.getItemId(position)
            if (!isMultiSelectMode) {
                enterMultiSelectMode()
                adapter.toggleMessageSelection(messageId)
                updateSelectedCount(adapter.getSelectedMessages().size)
            }
            true
        }
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
        when (currentChatType) {
            "GROUP" -> {
                ApiClient.apiService.getGroupMessages(currentGroupId)
                    .enqueue(object : Callback<List<ChatMessage>> {
                        override fun onResponse(call: retrofit2.Call<List<ChatMessage>>, response: retrofit2.Response<List<ChatMessage>>) {
                            if (response.isSuccessful) {
                                response.body()?.let { messages ->
                                    val groupMessages = messages.map { message ->
                                        message.copy(
                                            groupId = currentGroupId,
                                            receiverId = null,
                                            receiverName = null,
                                            chatType = "GROUP"
                                        )
                                    }
                                    adapter.setMessages(groupMessages)
                                    messagesList.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }

                        override fun onFailure(call: retrofit2.Call<List<ChatMessage>>, t: Throwable) {
                            Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            else -> {
                val currentUserId = UserPreferences.getUserId(this@ChatActivity)
                ApiClient.apiService.getPrivateMessages(currentUserId, currentReceiverId)
                    .enqueue(object : Callback<List<ChatMessage>> {
                        override fun onResponse(call: retrofit2.Call<List<ChatMessage>>, response: retrofit2.Response<List<ChatMessage>>) {
                            if (response.isSuccessful) {
                                response.body()?.let { messages ->
                                    val privateMessages = messages.map { message ->
                                        message.copy(
                                            receiverId = if (message.senderId == currentUserId) 
                                                currentReceiverId else currentUserId,
                                            chatType = "PRIVATE"
                                        )
                                    }
                                    adapter.setMessages(privateMessages)
                                    messagesList.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }

                        override fun onFailure(call: retrofit2.Call<List<ChatMessage>>, t: Throwable) {
                            Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
    }

    private fun setupWebSocket() {
        // 设置当前聊天的用户ID
        WebSocketManager.setCurrentChat(
            UserPreferences.getUserId(this),
            if (currentChatType == "GROUP") currentGroupId else currentReceiverId
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
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
        deleteButton.setOnClickListener {
            val selectedMessages = adapter.getSelectedMessages()
            if (selectedMessages.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("删除消息")
                    .setMessage("确定要删除选中的 ${selectedMessages.size} 条消息吗？")
                    .setPositiveButton("确定") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                selectedMessages.forEach { messageId ->
                                    deleteMessage(messageId)
                                }
                                exitMultiSelectMode()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@ChatActivity, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun hideDeleteButton() {
        deleteButton.visibility = View.GONE
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = when (currentChatType) {
                "private" -> receiverName
                "group" -> "$receiverName (群聊)"
                else -> receiverName
            }
        }
    }

    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        adapter.enterMultiSelectMode()
        showDeleteButton()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.exitMultiSelectMode()
        hideDeleteButton()
    }

    private fun deleteMessage(messageId: Long) {
        lifecycleScope.launch {
            try {
                println("🗑️ Starting message deletion process: $messageId")
                
                // 先从本地删除
                adapter.removeMessage(messageId)
                println("✅ Local message deletion completed")
                
                // 然后从服务器删除
                val response = ApiClient.apiService.deleteMessage(
                    messageId,
                    UserPreferences.getUserId(this@ChatActivity)
                )
                if (response.isSuccessful) {
                    response.body()?.let { deleteResponse ->
                        if (deleteResponse.isFullyDeleted) {
                            adapter.removeMessageCompletely(messageId)
                        }
                    }
                    println("✅ Server message deletion successful")
                    Toast.makeText(this@ChatActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                } else {
                    println("⚠️ Server deletion failed but local deletion succeeded: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error in deletion process: ${e.message}")
            }
        }
    }

    private fun uploadFile(uri: Uri) {
        val contentResolver = applicationContext.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val mediaType = contentResolver.getType(uri)
        
        // 根据 MIME 类型获取正确的文件扩展名
        val extension = when (mediaType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "video/mp4" -> ".mp4"
            else -> ".tmp"
        }
        
        val file = inputStream?.let { 
            createTempFile(it, "uploaded_file", extension) 
        }

        if (file != null) {
            val requestFile = file.asRequestBody(mediaType?.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            ApiClient.apiService.uploadFile(body).enqueue(object : retrofit2.Callback<FileDTO> {
                override fun onResponse(call: retrofit2.Call<FileDTO>, response: retrofit2.Response<FileDTO>) {
                    if (response.isSuccessful) {
                        response.body()?.let { fileDTO ->
                            handleFileUploadSuccess(fileDTO)
                        }
                    } else {
                        Toast.makeText(this@ChatActivity, "文件上传失败", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: retrofit2.Call<FileDTO>, t: Throwable) {
                    Toast.makeText(this@ChatActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun handleFileUploadSuccess(fileDTO: FileDTO) {
        println("⭐ Sending file message:")
        println("Content type: ${fileDTO.contentType}")
        println("Filename: ${fileDTO.filename}")
        println("URL: ${fileDTO.url}")

        // 使用完整的文件 URL 发送消息
        sendMessage(
            content = fileDTO.filename,
            type = "FILE",
            fileUrl = fileDTO.url  // 传入文件 URL
        )
    }

    private fun createTempFile(inputStream: InputStream, filename: String, extension: String): java.io.File? {
        return try {
            val tempDir = cacheDir
            val tempFile = java.io.File.createTempFile(filename, extension, tempDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onBackPressed() {
        if (isMultiSelectMode) {
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_search -> {
                showSearchMessagesDialog()
                true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.removeMessageListeners()
    }

    private fun showImagePreview(fileUrl: String?) {
        fileUrl?.let { url ->
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putExtra("imageUrl", "${getString(R.string.server_url_format).format(
                    getString(R.string.server_ip),
                    getString(R.string.server_port)
                )}$url")
            }
            startActivity(intent)
        }
    }

    private fun showVideoPreview(fileUrl: String?) {
        fileUrl?.let { url ->
            val intent = Intent(this, VideoPreviewActivity::class.java).apply {
                putExtra("videoUrl", "${getString(R.string.server_url_format).format(
                    getString(R.string.server_ip),
                    getString(R.string.server_port)
                )}$url")
            }
            startActivity(intent)
        }
    }

    private fun openFile(fileUrl: String?) {
        fileUrl?.let { url ->
            val fullUrl = "${getString(R.string.server_url_format).format(
                getString(R.string.server_ip),
                getString(R.string.server_port)
            )}$url"
            
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(fullUrl)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开此类型的文件", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // 添加辅助方法
    private fun isImageFile(extension: String): Boolean {
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private fun isVideoFile(extension: String): Boolean {
        return extension in listOf("mp4", "3gp", "mkv", "webm")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
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
                    type = currentChatType.lowercase()
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
        title = groupName
        println("🔄 Setting up group chat - GroupID: $groupId, Name: $groupName")
    }

    private fun setupPrivateChat(receiverId: Long, receiverName: String) {
        currentChatType = "PRIVATE"
        currentReceiverId = receiverId
        title = receiverName
        println("🔄 Setting up private chat - ReceiverID: $receiverId, Name: $receiverName")
    }

    private fun sendMessage(content: String, type: String = "TEXT", fileUrl: String? = null) {
        val userId = UserPreferences.getUserId(this)
        val username = UserPreferences.getUsername(this)
        
        val message = ChatMessage(
            id = null,
            senderId = userId,
            senderName = username,
            content = content,
            type = MessageType.valueOf(type),
            receiverId = if (currentChatType == "PRIVATE") currentReceiverId else null,
            receiverName = if (currentChatType == "PRIVATE") title else null,
            groupId = if (currentChatType == "GROUP") currentGroupId else null,
            timestamp = LocalDateTime.now(),
            fileUrl = fileUrl,  // 确保设置文件 URL
            chatType = currentChatType
        )
        
        // 发送到服务器
        WebSocketManager.sendMessage(message, 
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