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

    // ActivityResultLauncher for file picking
    private val filePickerLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 获取Intent传递的参数
        receiverId = intent.getLongExtra("receiver_id", 0)
        receiverName = intent.getStringExtra("receiver_name") ?: ""
        chatType = intent.getStringExtra("chat_type") ?: "private"

        // 设置Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = receiverName
            setDisplayHomeAsUpEnabled(true)
        }

        initViews()
        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        loadChatHistory()
        setupWebSocket()
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
        if (chatType == "group") {
            // 加载群聊消息
            ApiClient.apiService.getGroupMessages(receiverId)
                .enqueue(object : Callback<List<ChatMessage>> {
                    override fun onResponse(call: retrofit2.Call<List<ChatMessage>>, response: retrofit2.Response<List<ChatMessage>>) {
                        if (response.isSuccessful) {
                            response.body()?.let { messages ->
                                adapter.updateMessages(messages)
                                messagesList.scrollToPosition(adapter.itemCount - 1)
                            }
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<List<ChatMessage>>, t: Throwable) {
                        Toast.makeText(this@ChatActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            // 加载私聊消息
            ApiClient.apiService.getPrivateMessages(
                UserPreferences.getUserId(this),
                receiverId
            ).enqueue(object : Callback<List<ChatMessage>> {
                override fun onResponse(call: retrofit2.Call<List<ChatMessage>>, response: retrofit2.Response<List<ChatMessage>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { messages ->
                            adapter.updateMessages(messages)
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

    private fun setupWebSocket() {
        WebSocketManager.addMessageListener { message ->
            // 根据聊天类型判断是否显示消息
            val shouldShowMessage = when (chatType) {
                "group" -> message.groupId == receiverId  // 群聊消息
                else -> message.senderId == receiverId || message.receiverId == receiverId  // 私聊消息
            }

            if (shouldShowMessage) {
                runOnUiThread {
                    adapter.addMessage(message)
                    messagesList.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isEmpty()) return

        val message = ChatMessage(
            id = null,
            content = content,
            senderId = UserPreferences.getUserId(this),
            senderName = UserPreferences.getUsername(this),
            receiverId = if (chatType == "group") null else receiverId,  // 群聊时 receiverId 为 null
            receiverName = if (chatType == "group") null else receiverName,  // 群聊时 receiverName 为 null
            type = MessageType.TEXT,
            timestamp = LocalDateTime.now(),
            groupId = if (chatType == "group") receiverId else null  // 群聊时使用 receiverId 作为 groupId
        )

        WebSocketManager.sendMessage(message)
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
            title = receiverName
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
                            handleFileUploadResponse(fileDTO)
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

    private fun handleFileUploadResponse(response: FileDTO) {
        println("⭐ Sending file message:")
        println("Content type: ${response.contentType}")
        println("Filename: ${response.filename}")
        println("URL: ${response.url}")

        val message = ChatMessage(
            id = null,
            content = response.filename,
            senderId = UserPreferences.getUserId(this),
            senderName = UserPreferences.getUsername(this),
            receiverId = if (chatType == "group") null else receiverId,  // 群聊时 receiverId 为 null
            receiverName = if (chatType == "group") null else receiverName,  // 群聊时 receiverName 为 null
            type = MessageType.FILE,
            timestamp = LocalDateTime.now(),
            groupId = if (chatType == "group") receiverId else null,  // 群聊时使用 receiverId 作为 groupId
            fileUrl = response.url
        )

        println("⭐ Created message:")
        println("Type: ${message.type}")
        println("FileUrl: ${message.fileUrl}")

        WebSocketManager.sendMessage(message)
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
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchMessagesDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
}