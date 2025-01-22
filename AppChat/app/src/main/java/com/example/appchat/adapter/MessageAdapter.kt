package com.example.appchat.adapter

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.ImagePreviewActivity
import com.example.appchat.R
import com.example.appchat.VideoPreviewActivity
import com.example.appchat.db.ChatDatabase
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.util.CustomLongClickListener
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageAdapter(
    private val context: Context,
    private val currentUserId: Long,
    private val currentChatType: String,  // 'private' 或 'group'
    private val chatPartnerId: Long,  // 私聊对象ID或群组ID
    private val onMessageDelete: (Long) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    private val messages = mutableListOf<ChatMessage>()
    private val chatDatabase = ChatDatabase(context)
    private var highlightedPosition: Int = -1

    init {
        // 加载本地消息
        val loadedMessages = if (currentChatType == "private") {
            chatDatabase.getPrivateMessages(currentUserId, chatPartnerId)
        } else {
            chatDatabase.getGroupMessages(chatPartnerId)
        }
        messages.addAll(loadedMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        try {
            println("Adding message: ID=${message.id}, Type=${message.type}, Content=${message.content}")
            
            // 计算新消息应该插入的位置
            val insertPosition = calculateInsertPosition(message)
            println("Calculated insert position: $insertPosition")
            
            messages.add(insertPosition, message)
            if (message.type != MessageType.TIME) {
                if (currentChatType == "private") {
                    println("Saving private message to local database")
                    chatDatabase.saveMessage(
                        message,
                        chatType = "private",
                        receiverId = if (message.senderId == currentUserId) chatPartnerId else currentUserId
                    )
                } else {
                    println("Saving group message to local database")
                    chatDatabase.saveMessage(
                        message,
                        chatType = "group",
                        groupId = chatPartnerId
                    )
                }
                
                // 验证消息是否成功保存
                message.id?.let { messageId ->
                    if (chatDatabase.isMessageExists(messageId)) {
                        println("✅ Message verified in local database: $messageId")
                    } else {
                        println("❌ Message not found in local database: $messageId")
                    }
                }
            }
            notifyItemInserted(insertPosition)
            println("✅ Message added successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error adding message: ${e.message}")
        }
    }

    private fun calculateInsertPosition(newMessage: ChatMessage): Int {
        // 如果消息列表为空，直接插入到开始位置
        if (messages.isEmpty()) return 0

        // 找到第一个时间戳晚于新消息的位置
        val insertPosition = messages.indexOfFirst { 
            it.timestamp?.isAfter(newMessage.timestamp) == true 
        }

        // 如果没找到，说明新消息是最新的，添加到末尾
        return if (insertPosition == -1) messages.size else insertPosition
    }

    fun clearMessages() {
        messages.clear()
        if (currentChatType == "private") {
            chatDatabase.clearPrivateMessages(currentUserId, chatPartnerId)
        } else {
            chatDatabase.clearGroupMessages(chatPartnerId)
        }
        notifyDataSetChanged()
    }

    fun removeMessage(messageId: Long) {
        try {
            println("Starting to remove message: $messageId")
            
            // 先找到要删除的消息位置
            val position = messages.indexOfFirst { it.id == messageId }
            if (position != -1) {
                // 从内存中删除
                messages.removeAt(position)
                // 立即通知视图更新
                notifyItemRemoved(position)
                println("✅ Message removed from adapter at position: $position")
                
                // 然后从数据库中删除
                chatDatabase.deleteMessage(messageId)
                
                // 验证消息是否已被删除
                if (!chatDatabase.isMessageExists(messageId)) {
                    println("✅ Message successfully deleted from local database: $messageId")
                } else {
                    println("⚠️ Message still exists in database after deletion: $messageId")
                }
            } else {
                println("⚠️ Message not found in adapter: $messageId")
            }
            
            // 通知数据集可能发生变化
            notifyDataSetChanged()
            
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error removing message: ${e.message}")
        }
    }

    fun loadLocalMessages(): List<ChatMessage> {
        val localMessages = if (currentChatType == "private") {
            chatDatabase.getPrivateMessages(currentUserId, chatPartnerId)
        } else {
            chatDatabase.getGroupMessages(chatPartnerId)
        }
        
        messages.clear()
        messages.addAll(localMessages)
        notifyDataSetChanged()
        return localMessages
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        // 清除现有消息
        messages.clear()
        
        // 保存新消息到本地数据库
        newMessages.forEach { message ->
            if (message.type != MessageType.TIME) {
                if (currentChatType == "private") {
                    chatDatabase.saveMessage(
                        message,
                        chatType = "private",
                        receiverId = if (message.senderId == currentUserId) chatPartnerId else currentUserId
                    )
                } else {
                    chatDatabase.saveMessage(
                        message,
                        chatType = "group",
                        groupId = chatPartnerId
                    )
                }
            }
        }
        
        // 重新从本地数据库加载消息
        val localMessages = loadLocalMessages()
        println("✅ Updated local database with ${newMessages.size} messages, loaded ${localMessages.size} messages back")
    }

    fun searchMessages(query: String): List<Pair<ChatMessage, Int>> {
        return messages.mapIndexedNotNull { index, message ->
            if (message.content.contains(query, ignoreCase = true)) {
                Pair(message, index)
            } else null
        }
    }

    fun highlightMessage(position: Int) {
        // 清除之前的高亮
        clearHighlight()
        
        // 设置新的高亮
        highlightedPosition = position
        notifyItemChanged(position)
        
        // 3秒后取消高亮
        Handler(Looper.getMainLooper()).postDelayed({
            clearHighlight()
        }, 3000)
    }

    private fun clearHighlight() {
        val oldPosition = highlightedPosition
        highlightedPosition = -1
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_MY_MESSAGE) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        
        return MessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(layout, parent, false),
            currentUserId,
            onMessageDelete
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val previousMessage = if (position > 0) messages[position - 1] else null
        holder.bind(message, previousMessage)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_MY_MESSAGE
        } else {
            VIEW_TYPE_OTHER_MESSAGE
        }
    }

    inner class MessageViewHolder(
        itemView: View,
        private val currentUserId: Long,
        private val onMessageDelete: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val senderName: TextView = itemView.findViewById(R.id.senderName)
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val fileContainer: View? = itemView.findViewById(R.id.fileContainer)
        private val fileIcon: ImageView? = itemView.findViewById(R.id.fileIcon)
        
        init {
            // 在 messageContainer 上设置长按监听器
            itemView.findViewById<View>(R.id.messageContainer).setOnLongClickListener { view ->
                val message = messages[adapterPosition]
                message.id?.let { messageId ->
                    val menuItems = when {
                        message.type == MessageType.FILE && isImageFile(message.content.substringAfterLast('.', "")) -> {
                            arrayOf("删除消息", "保存图片")
                        }
                        else -> {
                            arrayOf("删除消息")
                        }
                    }

                    AlertDialog.Builder(view.context)
                        .setItems(menuItems) { dialog, which ->
                            when (which) {
                                0 -> {
                                    println("✅ Deleting message: $messageId")
                                    onMessageDelete(messageId)
                                }
                                1 -> {
                                    message.fileUrl?.let { url -> saveImage(url, message.content) }
                                }
                            }
                            dialog.dismiss()
                        }
                        .show()
                }
                true
            }
        }

        fun bind(message: ChatMessage, previousMessage: ChatMessage?) {
            // 如果是时间戳消息，只显示时间
            if (message.type == MessageType.TIME) {
                messageText.visibility = View.GONE
                fileContainer?.visibility = View.GONE
                timeText?.apply {
                    visibility = View.VISIBLE
                    text = message.timestamp?.let { formatTime(it) } ?: ""
                }
                return
            }

            // 正常消息的处理
            messageText.visibility = View.VISIBLE
            senderName.visibility = View.GONE
            
            // 处理时间戳显示逻辑
            timeText?.visibility = View.GONE  // 默认隐藏时间戳，由时间戳消息来显示

            when (message.type) {
                MessageType.TEXT -> {
                    messageText.text = message.content
                    fileIcon?.visibility = View.GONE
                    fileContainer?.visibility = View.GONE
                }
                MessageType.FILE -> {
                    val extension = message.content.substringAfterLast('.', "").lowercase()
                    when {
                        isImageFile(extension) -> {
                            messageText.text = message.content
                            fileIcon?.visibility = View.VISIBLE
                            fileContainer?.visibility = View.VISIBLE
                            // 加载缩略图
                            message.fileUrl?.let { url ->
                                Glide.with(itemView.context)
                                    .load(url)
                                    .override(200, 200)  // 增大缩略图尺寸
                                    .centerCrop()
                                    .into(fileIcon!!)
                            }
                            
                            // 点击打开图片预览
                            fileContainer?.setOnClickListener {
                                message.fileUrl?.let { url ->
                                    val intent = Intent(itemView.context, ImagePreviewActivity::class.java)
                                    intent.putExtra("imageUrl", url)
                                    itemView.context.startActivity(intent)
                                }
                            }
                        }
                        isVideoFile(extension) -> {
                            messageText.text = "🎥 ${message.content}"
                            
                            // 加载视频缩略图
                            message.fileUrl?.let { url ->
                                Glide.with(itemView.context)
                                    .asBitmap()
                                    .load(url)
                                    .override(200, 200)
                                    .centerCrop()
                                    .into(fileIcon!!)
                                
                                // 添加播放图标覆盖
                                val playIcon = itemView.findViewById<ImageView>(R.id.playIcon)
                                playIcon?.visibility = View.VISIBLE
                            }
                            
                            fileIcon?.visibility = View.VISIBLE
                            
                            // 点击播放视频
                            fileContainer?.setOnClickListener {
                                message.fileUrl?.let { url ->
                                    val intent = Intent(itemView.context, VideoPreviewActivity::class.java)
                                    intent.putExtra("videoUrl", url)
                                    itemView.context.startActivity(intent)
                                }
                            }
                        }
                        isPdfFile(extension) -> {
                            messageText.text = "📄 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_pdf)
                            fileIcon?.visibility = View.VISIBLE
                        }
                        isWordFile(extension) -> {
                            messageText.text = "📝 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_word)
                            fileIcon?.visibility = View.VISIBLE
                        }
                        else -> {
                            messageText.text = "📎 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_file)
                            fileIcon?.visibility = View.VISIBLE
                        }
                    }

                    fileContainer?.let { container ->
                        container.setOnClickListener {
                            message.fileUrl?.let { url -> 
                                when {
                                    isImageFile(extension) -> {
                                        // 打开图片预览
                                        val intent = Intent(itemView.context, ImagePreviewActivity::class.java)
                                        intent.putExtra("imageUrl", url)
                                        itemView.context.startActivity(intent)
                                    }
                                    isVideoFile(extension) -> {
                                        // 打开视频预览
                                        val intent = Intent(itemView.context, VideoPreviewActivity::class.java)
                                        intent.putExtra("videoUrl", url)
                                        itemView.context.startActivity(intent)
                                    }
                                    else -> {
                                        // 下载并打开文件
                                        downloadAndOpenFile(url, message.content)
                                    }
                                }
                            }
                        }
                    }
                }
                MessageType.IMAGE -> {
                    messageText.text = message.content
                    fileIcon?.visibility = View.GONE
                    fileContainer?.setOnClickListener(null)
                }
                MessageType.VIDEO -> {
                    messageText.text = message.content
                    fileIcon?.visibility = View.GONE
                    fileContainer?.setOnClickListener(null)
                }
                MessageType.TIME -> {
                    // TIME 类型的消息已经在前面处理过了，这里不需要额外处理
                    messageText.text = ""
                    fileIcon?.visibility = View.GONE
                    fileContainer?.visibility = View.GONE
                }
            }

            // 移除或修改这部分代码，让原始背景生效
            messageContainer.setBackgroundResource(
                if (adapterPosition == highlightedPosition) {
                    R.drawable.bg_message_highlighted
                } else {
                    // 不要在这里设置背景，让布局文件中的背景生效
                    0  // 0 表示不设置背景
                }
            )

            // 或者改为：
            if (adapterPosition == highlightedPosition) {
                messageContainer.setBackgroundResource(R.drawable.bg_message_highlighted)
            } else {
                // 根据消息类型设置不同的背景
                val backgroundRes = if (message.senderId == currentUserId) {
                    R.drawable.bg_message_sent
                } else {
                    R.drawable.bg_message_received
                }
                messageContainer.setBackgroundResource(backgroundRes)
            }
        }

        private fun isImageFile(extension: String): Boolean {
            return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
        }

        private fun isVideoFile(extension: String): Boolean {
            return extension.lowercase() in listOf(
                "mp4", "3gp", "mkv", "webm", "avi", 
                "mov", "wmv", "flv"
            )
        }

        private fun isPdfFile(extension: String): Boolean {
            return extension == "pdf"
        }

        private fun isWordFile(extension: String): Boolean {
            return extension in listOf("doc", "docx")
        }

        private fun downloadAndOpenFile(url: String, filename: String) {
            try {
                println("Starting download: $url")
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "chat_files"
                ).apply { mkdirs() }

                val file = File(downloadDir, filename)
                
                // 如果文件已存在且大小不为0，直接打开
                if (file.exists() && file.length() > 0) {
                    println("File already exists, opening directly")
                    openFile(file)
                    return
                }

                val request = DownloadManager.Request(Uri.parse(url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(file))
                    .setTitle("下载文件")
                    .setDescription(filename)
                    .setMimeType(getMimeType(filename))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val downloadManager = itemView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = downloadManager.enqueue(request)

                println("Download started with ID: $downloadId")

                // 注册下载完成的广播接收器
                val onComplete = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            try {
                                context.unregisterReceiver(this)
                                
                                // 检查下载是否成功
                                val query = DownloadManager.Query().setFilterById(downloadId)
                                val cursor = downloadManager.query(query)
                                if (cursor.moveToFirst()) {
                                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                        println("Download successful, opening file")
                                        openFile(file)
                                    } else {
                                        println("Download failed with status: $status")
                                        Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                cursor.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                println("Error handling download completion: ${e.message}")
                            }
                        }
                    }
                }

                // 使用新的注册方式，指定接收器不导出
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    itemView.context.registerReceiver(
                        onComplete,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    itemView.context.registerReceiver(
                        onComplete,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    )
                }

                Toast.makeText(itemView.context, "开始下载: $filename", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                println("Download error: ${e.message}")
                Toast.makeText(itemView.context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun openFile(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    itemView.context,
                    "${itemView.context.packageName}.fileprovider",
                    file
                )
                
                // 对于 PDF 文件特别处理
                val mimeType = getMimeType(file.name)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  // 添加这个标志
                }

                // 检查是否有应用可以处理此意图
                if (intent.resolveActivity(itemView.context.packageManager) != null) {
                    itemView.context.startActivity(intent)
                } else {
                    Toast.makeText(
                        itemView.context,
                        "请安装 PDF 阅读器应用",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    itemView.context,
                    "打开文件失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun getMimeType(filename: String): String {
            return when (filename.substringAfterLast('.', "").lowercase()) {
                // 视频格式
                "mp4" -> "video/mp4"
                "3gp" -> "video/3gpp"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "wmv" -> "video/x-ms-wmv"
                "flv" -> "video/x-flv"
                // 现有的格式
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "txt" -> "text/plain"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "*/*"
            }
        }

        private fun formatTime(timestamp: LocalDateTime): String {
            return try {
                DateTimeFormatter.ofPattern("HH:mm").format(timestamp)
            } catch (e: Exception) {
                ""
            }
        }

        private fun saveImage(imageUrl: String, filename: String) {
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(downloadDir, "AppChat").apply { mkdirs() }
                val file = File(appDir, filename)

                val request = DownloadManager.Request(Uri.parse(imageUrl))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(file))
                    .setTitle("保存图片")
                    .setDescription(filename)
                    .setMimeType("image/*")

                val downloadManager = itemView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)

                Toast.makeText(itemView.context, "图片保存中...", Toast.LENGTH_SHORT).show()

                // 通知图库更新
                itemView.context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(file)
                })
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(itemView.context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun Context.isDestroyed(): Boolean {
            return when (this) {
                is android.app.Activity -> this.isDestroyed
                is android.content.ContextWrapper -> this.baseContext.isDestroyed()
                else -> false
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
    }
} 