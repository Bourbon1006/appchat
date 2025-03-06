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
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.ImagePreviewActivity
import com.example.appchat.MainActivity
import com.example.appchat.R
import com.example.appchat.VideoPreviewActivity
import com.example.appchat.db.ChatDatabase
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.util.CustomLongClickListener
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.media.MediaMetadataRetriever

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
    private var isMultiSelectMode = false
    private val selectedMessages = mutableSetOf<Long>()

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

    fun removeMessageCompletely(messageId: Long) {
        // 从内存中移除消息
        val iterator = messages.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            if (message.id == messageId) {
                iterator.remove()
                break
            }
        }
        
        // 从本地数据库中完全删除消息
        chatDatabase.deleteMessage(messageId)
        
        notifyDataSetChanged()
    }

    fun removeMessage(messageId: Long) {
        // 从内存中移除消息
        val iterator = messages.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            if (message.id == messageId) {
                iterator.remove()
                break
            }
        }
        
        // 在本地数据库中标记消息为当前用户已删除
        if (currentChatType == "private") {
            chatDatabase.markMessageAsDeleted(messageId, currentUserId)
        } else {
            chatDatabase.deleteMessage(messageId)  // 群聊消息直接删除
        }
        
        notifyDataSetChanged()
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
            } else {
                null
            }
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

    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedMessages.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedMessages(): Set<Long> = selectedMessages.toSet()

    fun removeMessages(messageIds: Set<Long>) {
        val iterator = messages.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            if (messageIds.contains(message.id)) {
                iterator.remove()
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_MY_MESSAGE -> R.layout.item_message_sent
            else -> R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view, currentUserId, onMessageDelete)
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
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val fileContainer: View? = itemView.findViewById(R.id.fileContainer)
        private val fileIcon: ImageView? = itemView.findViewById(R.id.fileIcon)
        private val playIcon: ImageView? = itemView.findViewById(R.id.playIcon)
        private val avatarImage: ImageView = itemView.findViewById(R.id.messageAvatar)
        private val checkbox: CheckBox = itemView.findViewById(R.id.messageCheckbox)
        
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
            messageText.text = message.content

            // 加载发送者头像
            val avatarUrl = "${itemView.context.getString(R.string.server_url_format).format(
                itemView.context.getString(R.string.server_ip),
                itemView.context.getString(R.string.server_port)
            )}/api/users/${message.senderId}/avatar"
            
            Glide.with(itemView.context)
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(avatarImage)

            // 如果是时间戳消息，只显示时间
            if (message.type == MessageType.TIME) {
                messageText.visibility = View.GONE
                fileContainer?.visibility = View.GONE
                timeText.apply {
                    visibility = View.VISIBLE
                    text = message.timestamp?.let { formatTime(it) } ?: ""
                }
                return
            }

            // 正常消息的处理
            messageText.visibility = View.VISIBLE
            
            // 处理时间戳显示逻辑
            timeText.visibility = View.GONE  // 默认隐藏时间戳，由时间戳消息来显示

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
                            messageText.visibility = View.GONE
                            fileIcon?.visibility = View.VISIBLE
                            fileContainer?.visibility = View.VISIBLE
                            message.fileUrl?.let { url ->
                                Glide.with(itemView.context)
                                    .load(url)
                                    .override(400, 400)
                                    .centerCrop()
                                    .into(fileIcon!!)
                            }
                        }
                        isVideoFile(extension) -> {
                            messageText.visibility = View.GONE
                            fileIcon?.visibility = View.VISIBLE
                            fileContainer?.visibility = View.VISIBLE
                            playIcon?.visibility = View.VISIBLE

                            // 使用 MediaMetadataRetriever 获取本地视频缩略图
                            message.fileUrl?.let { url ->
                                // 先下载视频文件到缓存目录
                                val cacheDir = itemView.context.cacheDir
                                val videoFile = File(cacheDir, message.content)
                                
                                if (!videoFile.exists()) {
                                    // 如果视频文件不存在，显示默认缩略图
                                    Glide.with(itemView.context)
                                        .load(R.drawable.video_placeholder)
                                        .override(400, 400)
                                        .centerCrop()
                                        .into(fileIcon!!)
                                } else {
                                    try {
                                        val retriever = MediaMetadataRetriever()
                                        retriever.setDataSource(videoFile.absolutePath)
                                        val bitmap = retriever.getFrameAtTime(0)
                                        retriever.release()

                                        if (bitmap != null) {
                                            fileIcon?.setImageBitmap(bitmap)
                                        } else {
                                            fileIcon?.setImageResource(R.drawable.video_placeholder)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        fileIcon?.setImageResource(R.drawable.video_placeholder)
                                    }
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
                            handleFileClick(message)
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

            // 处理多选模式
            checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            checkbox.isChecked = message.id?.let { selectedMessages.contains(it) } ?: false

            // 设置点击监听器
            itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    message.id?.let { messageId ->
                        toggleMessageSelection(messageId)
                    }
                }
            }

            // 设置长按监听器
            itemView.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    (context as MainActivity).enterMultiSelectMode()
                    message.id?.let { messageId ->
                        toggleMessageSelection(messageId)
                    }
                }
                true
            }

            // 设置 CheckBox 的点击监听器
            checkbox.setOnClickListener {
                message.id?.let { messageId ->
                    toggleMessageSelection(messageId)
                }
            }
        }

        private fun toggleMessageSelection(messageId: Long) {
            if (selectedMessages.contains(messageId)) {
                selectedMessages.remove(messageId)
            } else {
                selectedMessages.add(messageId)
            }
            notifyItemChanged(adapterPosition)
            (context as MainActivity).updateSelectedCount(selectedMessages.size)
        }

        private fun isImageFile(extension: String): Boolean {
            return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
        }

        private fun isVideoFile(extension: String): Boolean {
            return extension in listOf("mp4", "3gp", "mkv", "webm")
        }

        private fun isPdfFile(extension: String): Boolean {
            return extension == "pdf"
        }

        private fun isWordFile(extension: String): Boolean {
            return extension in listOf("doc", "docx")
        }

        private fun handleFileClick(message: ChatMessage) {
            val extension = message.content.substringAfterLast('.', "").lowercase()
            message.fileUrl?.let { url ->
                when {
                    isImageFile(extension) -> {
                        // 显示保存图片的选项
                        AlertDialog.Builder(itemView.context)
                            .setTitle("图片操作")
                            .setItems(arrayOf("查看", "保存")) { _, which ->
                                when (which) {
                                    0 -> {
                                        // 查看图片
                                        val intent = Intent(itemView.context, ImagePreviewActivity::class.java)
                                        intent.putExtra("imageUrl", url)
                                        itemView.context.startActivity(intent)
                                    }
                                    1 -> {
                                        // 保存图片
                                        downloadAndSaveFile(url, message.content, "image/*")
                                    }
                                }
                            }
                            .show()
                    }
                    isVideoFile(extension) -> {
                        // 显示视频操作选项
                        AlertDialog.Builder(itemView.context)
                            .setTitle("视频操作")
                            .setItems(arrayOf("播放", "保存")) { _, which ->
                                when (which) {
                                    0 -> {
                                        // 播放视频
                                        val intent = Intent(itemView.context, VideoPreviewActivity::class.java)
                                        intent.putExtra("videoUrl", url)
                                        itemView.context.startActivity(intent)
                                    }
                                    1 -> {
                                        // 保存视频
                                        downloadAndSaveFile(url, message.content, "video/*")
                                    }
                                }
                            }
                            .show()
                    }
                    else -> {
                        // 其他文件直接下载
                        downloadAndSaveFile(url, message.content, "*/*")
                    }
                }
            }
        }

        private fun downloadAndSaveFile(url: String, filename: String, mimeType: String) {
            try {
                val context = itemView.context
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(filename)
                    .setDescription("正在下载...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    .setMimeType(mimeType)

                // 注册下载完成的广播接收器
                val downloadReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (downloadId != -1L) {
                            val query = downloadId?.let { DownloadManager.Query().setFilterById(it) }
                            val cursor = downloadManager.query(query)
                            
                            if (cursor.moveToFirst()) {
                                val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val status = cursor.getInt(columnIndex)
                                
                                when (status) {
                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        Toast.makeText(context, "文件已保存到下载目录", Toast.LENGTH_SHORT).show()
                                    }
                                    DownloadManager.STATUS_FAILED -> {
                                        Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            cursor.close()
                        }
                        context?.unregisterReceiver(this)
                    }
                }

                // 注册广播接收器
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
                )

                // 开始下载
                downloadManager.enqueue(request)
                Toast.makeText(context, "开始下载...", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(itemView.context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
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