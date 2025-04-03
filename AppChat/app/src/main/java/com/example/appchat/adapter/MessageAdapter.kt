package com.example.appchat.adapter

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.activity.ImagePreviewActivity
import com.example.appchat.R
import com.example.appchat.activity.VideoPreviewActivity
import com.example.appchat.db.ChatDatabase
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.media.MediaScannerConnection
import com.example.appchat.util.UserPreferences
import com.example.appchat.activity.FilePreviewActivity
import com.bumptech.glide.request.target.Target
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.MediaStore
import android.os.Build
import android.content.ContentValues
import android.graphics.Bitmap
import com.example.appchat.api.ApiService
import java.io.FileOutputStream
import android.net.Uri as AndroidUri
import androidx.core.content.FileProvider
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Color
import androidx.core.content.ContextCompat

class MessageAdapter(
    private val context: Context,
    private val currentUserId: Long,
    private val currentChatType: String,  // 'private' 或 'group'
    private val chatPartnerId: Long,  // 私聊对象ID或群组ID
    private val onMessageLongClick: (Int) -> Boolean,  // 添加长按回调
    private val onMessageClick: (Int) -> Unit,         // 添加点击回调
    private val onMessageDelete: (Long) -> Unit        // 保留原有的删除回调
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val chatDatabase = ChatDatabase(context)
    private var highlightedPosition: Int = -1
    private var isMultiSelectMode = false
    private val selectedMessages = mutableSetOf<Long>()
    private var onItemLongClickListener: ((Int) -> Boolean)? = null
    private var onItemClickListener: ((Int) -> Unit)? = null
    private var highlightedMessageId: Long = -1L
    private val handler = Handler(Looper.getMainLooper())

    fun setOnItemLongClickListener(listener: (Int) -> Boolean) {
        onItemLongClickListener = listener
    }

    fun setOnItemClickListener(listener: (Int) -> Unit) {
        onItemClickListener = listener
    }

    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        notifyDataSetChanged()
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedMessages.clear()
        notifyDataSetChanged()
    }

    fun toggleMessageSelection(position: Int) {
        messages[position].id?.let { messageId ->
            if (selectedMessages.contains(messageId)) {
                selectedMessages.remove(messageId)
            } else {
                selectedMessages.add(messageId)
            }
            notifyItemChanged(position)
        }
    }

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
        messages.add(message)
        notifyItemInserted(messages.size - 1)
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
        val position = messages.indexOfFirst { it.id == messageId }
        if (position != -1) {
            messages.removeAt(position)
            selectedMessages.remove(messageId)
            notifyItemRemoved(position)
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

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
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
        val oldPosition = highlightedPosition
        highlightedPosition = position
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        notifyItemChanged(position)
    }

    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedMessages.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedMessages(): List<ChatMessage> {
        return messages.filter { it.id in selectedMessages }
    }

    fun removeMessages(messageIds: List<Long>) {
        val iterator = messages.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            if (message.id in messageIds) {
                iterator.remove()
            }
        }
        selectedMessages.removeAll(messageIds.toSet())
        notifyDataSetChanged()
    }

    fun getMessage(messageId: Long): ChatMessage? {
        return messages.find { it.id == messageId }
    }

    fun getMessages(): List<ChatMessage> = messages.toList()

    fun clearSelectedMessages() {
        selectedMessages.clear()
        notifyDataSetChanged()
    }

    fun setHighlightedMessageId(messageId: Long) {
        highlightedMessageId = messageId
        notifyDataSetChanged()
        
        // 3秒后自动取消高亮
        handler.postDelayed({
            highlightedMessageId = -1L
            notifyDataSetChanged()
        }, 3000) // 3000毫秒 = 3秒
    }

    fun findMessagePosition(messageId: Long): Int {
        return messages.indexOfFirst { it.id == messageId }
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
        
        // 如果是高亮消息，设置特殊背景
        if (message.id == highlightedMessageId) {
            // 直接设置背景资源，不使用背景颜色
            holder.itemView.setBackgroundResource(R.color.search_highlight)
            
            // 添加闪烁动画效果
            holder.itemView.animate()
                .alpha(0.5f)
                .setDuration(500)
                .withEndAction {
                    holder.itemView.animate()
                        .alpha(1.0f)
                        .setDuration(500)
                        .start()
                }
                .start()
        } else {
            // 根据是否选中设置背景
            holder.itemView.setBackgroundResource(
                if (message.id in selectedMessages) 
                    R.drawable.selected_message_background 
                else 
                    android.R.color.transparent
            )
        }
        
        // 显示/隐藏复选框并设置状态
        holder.checkbox.apply {
            visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            isChecked = message.id in selectedMessages
        }
        
        // 设置长按和点击事件
        holder.itemView.setOnLongClickListener {
            onMessageLongClick(position)
        }
        
        holder.itemView.setOnClickListener {
            onMessageClick(position)
        }

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

    override fun getItemId(position: Int): Long {
        return messages[position].id ?: -1L
    }

    inner class MessageViewHolder(
        itemView: View,
        private val currentUserId: Long,
        private val onMessageDelete: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
            ?: throw IllegalStateException("Required view 'messageText' not found")
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
            ?: throw IllegalStateException("Required view 'timeText' not found")
        private val fileContainer: View? = itemView.findViewById(R.id.fileContainer)
        private val fileIcon: ImageView? = itemView.findViewById(R.id.fileIcon)
        private val playIcon: ImageView? = itemView.findViewById(R.id.playIcon)
        private val avatarImage: ImageView = itemView.findViewById(R.id.avatar)
            ?: throw IllegalStateException("Required view 'avatar' not found")
        val checkbox: CheckBox = itemView.findViewById(R.id.messageCheckbox)
            ?: throw IllegalStateException("Required view 'messageCheckbox' not found")
        private val senderName: TextView = itemView.findViewById(R.id.senderName)

        fun bind(message: ChatMessage, previousMessage: ChatMessage?) {
            // 加载发送者头像
            val baseUrl = getBaseUrl(itemView.context)

            // 根据消息类型构建头像URL
            val avatarUrl = when (currentChatType) {
                "GROUP" -> "$baseUrl/api/users/${message.senderId}/avatar"  // 群聊显示发送者头像
                else -> "$baseUrl/api/users/${message.senderId}/avatar"     // 私聊显示对方头像
            }

            // 加载头像
            Glide.with(itemView.context)
                .load(avatarUrl)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .circleCrop()
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(avatarImage)

            // 始终设置用户名，不管头像是否加载
            if (message.senderId == currentUserId) {
                // 对于自己发送的消息，使用缓存的昵称
                val nickname = UserPreferences.getUserNickname(itemView.context)
                senderName.text = if (!nickname.isNullOrEmpty()) nickname else message.senderName
            } else {
                // 对于接收的消息，使用消息中的发送者名称
                senderName.text = message.senderName
            }
            senderName.visibility = View.VISIBLE

            // 处理消息内容
            when (message.type) {
                MessageType.TIME -> handleTimeMessage(message)
                MessageType.TEXT -> handleTextMessage(message)
                MessageType.FILE -> handleFileMessage(message)
                MessageType.IMAGE -> handleImageMessage(message)
                MessageType.VIDEO -> handleVideoMessage(message)
            }

            // 在群聊中显示发送者名称（除了自己发的消息）
            senderName.apply {
                if (currentChatType == "GROUP" && message.senderId != currentUserId) {
                    visibility = View.VISIBLE
                    text = message.senderName
                } else {
                    visibility = View.GONE
                }
            }
        }

        private fun handleTimeMessage(message: ChatMessage) {
            messageText.visibility = View.GONE
            fileContainer?.visibility = View.GONE
            timeText.apply {
                visibility = View.VISIBLE
                text = message.timestamp?.let { formatTime(it) } ?: ""
            }
        }

        private fun handleTextMessage(message: ChatMessage) {
            messageText.visibility = View.VISIBLE
            messageText.text = message.content
            fileContainer?.visibility = View.GONE
        }

        private fun handleFileMessage(message: ChatMessage) {
            val fileUrl = message.fileUrl
            val fileName = message.content
            val extension = fileName.substringAfterLast(".", "").lowercase()
            
            // 确保文件容器可见
            fileContainer?.visibility = View.VISIBLE
            fileIcon?.visibility = View.VISIBLE
            
            // 根据文件类型设置不同的图标和处理方式
            when {
                isImageFile(extension) -> {
                    // 显示图片缩略图
                    messageText.visibility = View.GONE
                    fileContainer?.visibility = View.VISIBLE
                    fileIcon?.visibility = View.VISIBLE
                    playIcon?.visibility = View.GONE
                    
                    // 加载图片缩略图 - 确保使用完整URL
                    fileUrl?.let { url ->
                        // 检查URL是否包含完整路径，如果不是，添加基础URL
                        val fullUrl = if (url.startsWith("http")) {
                            url
                        } else {
                            val baseUrl = getBaseUrl(itemView.context)
                            if (url.startsWith("/")) baseUrl + url else "$baseUrl/$url"
                        }
                        
                        // 设置图片容器的最大宽度为屏幕宽度的70%
                        val displayMetrics = itemView.context.resources.displayMetrics
                        val maxWidth = (displayMetrics.widthPixels * 0.7).toInt()
                        
                        // 使用FitCenter而不是CenterCrop，保持图片比例
                        Glide.with(itemView.context)
                            .load(fullUrl)
                            .apply(RequestOptions()
                                .override(maxWidth, Target.SIZE_ORIGINAL) // 设置最大宽度，高度自适应
                                .fitCenter() // 保持图片比例
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.ic_image_loading)
                                .error(R.drawable.ic_image_error))
                            .into(fileIcon!!)
                        
                        // 设置图片容器的宽高为自适应
                        fileIcon.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        fileIcon.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        fileIcon.adjustViewBounds = true // 允许视图根据图片比例调整大小
                        fileIcon.maxWidth = maxWidth // 设置最大宽度
                        fileIcon.maxHeight = maxWidth // 设置最大高度，防止图片过高
                    }
                    
                    // 点击查看大图
                    fileContainer?.setOnClickListener {
                        showImagePreview(fileUrl)
                    }
                }
                isVideoFile(extension) -> {
                    // 显示视频缩略图
                    messageText.visibility = View.GONE
                    fileContainer?.visibility = View.VISIBLE
                    fileIcon?.visibility = View.VISIBLE
                    playIcon?.visibility = View.VISIBLE
                    
                    // 设置视频容器的最大宽度为屏幕宽度的70%
                    val displayMetrics = itemView.context.resources.displayMetrics
                    val maxWidth = (displayMetrics.widthPixels * 0.7).toInt()
                    
                    // 加载视频缩略图 - 确保使用完整URL
                    fileUrl?.let { url ->
                        // 检查URL是否包含完整路径，如果不是，添加基础URL
                        val fullUrl = if (url.startsWith("http")) {
                            url
                        } else {
                            val baseUrl = getBaseUrl(itemView.context)
                            if (url.startsWith("/")) baseUrl + url else "$baseUrl/$url"
                        }
                        
                        Glide.with(itemView.context)
                            .load(fullUrl)
                            .apply(RequestOptions()
                                .frame(1000000) // 获取视频第一帧作为缩略图
                                .override(maxWidth, Target.SIZE_ORIGINAL) // 设置最大宽度，高度自适应
                                .fitCenter() // 保持视频比例
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.video_placeholder)
                                .error(R.drawable.video_placeholder))
                            .into(fileIcon!!)
                        
                        // 设置视频容器的宽高为自适应
                        fileIcon.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        fileIcon.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        fileIcon.adjustViewBounds = true // 允许视图根据视频比例调整大小
                        fileIcon.maxWidth = maxWidth // 设置最大宽度
                        fileIcon.maxHeight = maxWidth // 设置最大高度，防止视频过高
                    }
                    
                    // 点击播放视频
                    fileContainer?.setOnClickListener {
                        showVideoPreview(fileUrl)
                    }
                }
                isPdfFile(extension) -> {
                    // PDF 文件特殊处理
                    messageText.text = "📄 ${message.content}"
                    fileIcon?.setImageResource(R.drawable.ic_pdf)
                    
                    // 添加日志以便调试
                    Log.d("MessageAdapter", "PDF file detected: $fileName, URL: $fileUrl")
                    
                    // 确保点击事件被正确设置
                    itemView.setOnClickListener {
                        Log.d("MessageAdapter", "PDF file clicked: $fileName")
                        showFilePreview(fileUrl, fileName)
                    }
                    
                    // 文件容器的点击事件
                    fileContainer?.setOnClickListener {
                        Log.d("MessageAdapter", "PDF file container clicked: $fileName")
                        showFilePreview(fileUrl, fileName)
                    }
                }
                isWordFile(extension) -> {
                    messageText.text = "📝 ${message.content}"
                    fileIcon?.setImageResource(R.drawable.ic_word)
                    fileContainer?.setOnClickListener {
                        showFilePreview(fileUrl, fileName)
                    }
                }
                isExcelFile(extension) -> {
                    messageText.text = "📊 ${message.content}"
                    fileIcon?.setImageResource(R.drawable.ic_excel)
                    fileContainer?.setOnClickListener {
                        showFilePreview(fileUrl, fileName)
                    }
                }
                isPptFile(extension) -> {
                    messageText.text = "📊 ${message.content}"
                    fileIcon?.setImageResource(R.drawable.ic_ppt)
                    fileContainer?.setOnClickListener {
                        showFilePreview(fileUrl, fileName)
                    }
                }
                else -> {
                    messageText.text = "📎 ${message.content}"
                    fileIcon?.setImageResource(R.drawable.ic_file)
                    fileContainer?.setOnClickListener {
                        showFilePreview(fileUrl, fileName)
                    }
                }
            }
            
            // 添加长按事件
            fileContainer?.setOnLongClickListener {
                showFileOptions(itemView.context, fileUrl, fileName, extension)
                true
            }
        }

        private fun handleImageMessage(message: ChatMessage) {
            messageText.text = message.content
            fileIcon?.visibility = View.GONE
            fileContainer?.setOnClickListener(null)
            
            // 添加长按事件
            fileContainer?.setOnLongClickListener {
                val extension = message.content.substringAfterLast(".", "").lowercase()
                showFileOptions(itemView.context, message.fileUrl, message.content, extension)
                true
            }
        }

        private fun handleVideoMessage(message: ChatMessage) {
            messageText.text = message.content
            fileIcon?.visibility = View.GONE
            fileContainer?.setOnClickListener(null)
            
            // 添加长按事件
            fileContainer?.setOnLongClickListener {
                val extension = message.content.substringAfterLast(".", "").lowercase()
                showFileOptions(itemView.context, message.fileUrl, message.content, extension)
                true
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
    }

    // 文件类型判断的辅助方法保持在适配器类级别
    private fun isImageFile(extension: String): Boolean {
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private fun isVideoFile(extension: String): Boolean {
        return extension in listOf("mp4", "3gp", "mkv", "webm")
    }

    private fun isPdfFile(extension: String): Boolean {
        return extension.equals("pdf", ignoreCase = true)
    }

    private fun isWordFile(extension: String): Boolean {
        return extension in listOf("doc", "docx", "txt")
    }

    private fun isExcelFile(extension: String): Boolean {
        return extension in listOf("xls", "xlsx")
    }

    private fun isPptFile(extension: String): Boolean {
        return extension in listOf("ppt", "pptx")
    }

    private fun formatTime(timestamp: LocalDateTime): String {
        return try {
            DateTimeFormatter.ofPattern("HH:mm").format(timestamp)
        } catch (e: Exception) {
            ""
        }
    }

    private fun Context.isDestroyed(): Boolean {
        return when (this) {
            is android.app.Activity -> this.isDestroyed
            is android.content.ContextWrapper -> this.baseContext.isDestroyed()
            else -> false
        }
    }

    private fun showFileOptions(context: Context, fileUrl: String?, fileName: String, fileType: String) {
        if (fileUrl == null) {
            Toast.makeText(context, "无法操作：文件URL为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = mutableListOf<String>()
        
        // 根据文件类型添加不同的选项
        when {
            isImageFile(fileType) -> {
                options.add("查看图片")
                options.add("保存到相册")
                options.add("分享图片")
            }
            isVideoFile(fileType) -> {
                options.add("播放视频")
                options.add("保存视频")
                options.add("分享视频")
            }
            isPdfFile(fileType) || isWordFile(fileType) || isExcelFile(fileType) || isPptFile(fileType) -> {
                options.add("预览文件")
                options.add("下载文件")
                options.add("分享文件")
            }
            else -> {
                options.add("下载文件")
                options.add("分享文件")
            }
        }
        
        // 显示选项对话框
        AlertDialog.Builder(context)
            .setTitle("文件操作")
            .setItems(options.toTypedArray()) { dialog, which ->
                val fullUrl = if (fileUrl.startsWith("http")) {
                    fileUrl
                } else {
                    "${getBaseUrl(context)}$fileUrl"
                }
                
                when (options[which]) {
                    "查看图片" -> showImagePreview(fileUrl)
                    "保存到相册" -> {
                        if (isImageFile(fileType)) {
                            saveImageToGallery(context, fileUrl)
                        }
                    }
                    "播放视频" -> showVideoPreview(fileUrl)
                    "预览文件" -> {
                        val intent = Intent(context, FilePreviewActivity::class.java).apply {
                            putExtra("fileUrl", fullUrl)
                            putExtra("fileName", fileName)
                            putExtra("fileType", fileType)
                        }
                        context.startActivity(intent)
                    }
                    "下载文件" -> openFile(fullUrl)
                    "保存视频" -> {
                        // 实现视频保存功能
                        Toast.makeText(context, "视频保存功能即将推出", Toast.LENGTH_SHORT).show()
                    }
                    "分享图片", "分享视频", "分享文件" -> {
                        // 实现分享功能
                        shareFile(context, fullUrl, fileName, fileType)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun shareFile(context: Context, fileUrl: String, fileName: String, fileType: String) {
        // 首先下载文件到缓存目录
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 创建缓存目录
                val cacheDir = File(context.cacheDir, "shared_files")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                // 下载文件
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                
                // 创建缓存文件
                val cacheFile = File(cacheDir, fileName)
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(cacheFile)
                
                // 复制文件内容
                val buffer = ByteArray(1024)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }
                
                outputStream.close()
                inputStream.close()
                
                // 获取文件的 MIME 类型
                val mimeType = getMimeTypeFromExtension(fileType)
                
                // 创建分享意图
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        cacheFile
                    )
                    
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = mimeType
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MessageAdapter", "Error sharing file: ${e.message}", e)
                    Toast.makeText(context, "分享文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getMimeTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mp3"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    private fun getBaseUrl(context: Context): String {
        val serverIp = context.getString(R.string.server_ip)
        val serverPort = context.getString(R.string.server_port)
        return context.getString(R.string.server_http_url_format, serverIp, serverPort)
    }

    private fun showImagePreview(imageUrl: String?) {
        if (imageUrl == null) return
        
        // 确保使用完整URL
        val fullUrl = if (imageUrl.startsWith("http")) {
            imageUrl
        } else {
            val baseUrl = getBaseUrl(context)
            if (imageUrl.startsWith("/")) baseUrl + imageUrl else "$baseUrl/$imageUrl"
        }
        
        val intent = Intent(context, ImagePreviewActivity::class.java).apply {
            putExtra("imageUrl", fullUrl)
        }
        context.startActivity(intent)
    }

    private fun showVideoPreview(videoUrl: String?) {
        if (videoUrl == null) return
        
        // 确保使用完整URL
        val fullUrl = if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            val baseUrl = getBaseUrl(context)
            if (videoUrl.startsWith("/")) baseUrl + videoUrl else "$baseUrl/$videoUrl"
        }
        
        val intent = Intent(context, VideoPreviewActivity::class.java).apply {
            putExtra("videoUrl", fullUrl)
        }
        context.startActivity(intent)
    }

    private fun openFile(fileUrl: String?) {
        if (fileUrl == null) return
        
        val fileName = fileUrl.substringAfterLast("/")
        val extension = fileName.substringAfterLast(".", "")
        
        // 创建下载请求
        val request = DownloadManager.Request(AndroidUri.parse(fileUrl))
            .setTitle(fileName)
            .setDescription("正在下载文件...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // 获取下载管理器
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // 开始下载
        val downloadId = downloadManager.enqueue(request)
        
        // 注册下载完成的广播接收器
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Toast.makeText(context, "下载完成", Toast.LENGTH_SHORT).show()
                    context.unregisterReceiver(this)
                    
                    // 通知系统扫描新文件
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null
                    ) { _, uri ->
                        // 可以在这里处理扫描完成后的操作
                    }
                }
            }
        }
        
        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private fun showFilePreview(fileUrl: String?, fileName: String) {
        if (fileUrl == null) {
            Log.e("MessageAdapter", "Cannot preview file: URL is null")
            Toast.makeText(context, "无法预览文件：URL为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("MessageAdapter", "Showing file preview for: $fileName, URL: $fileUrl")
        
        // 确保使用完整URL
        val fullUrl = if (fileUrl.startsWith("http")) {
            fileUrl
        } else {
            val baseUrl = getBaseUrl(context)
            if (fileUrl.startsWith("/")) baseUrl + fileUrl else "$baseUrl/$fileUrl"
        }
        
        Log.d("MessageAdapter", "Full URL for preview: $fullUrl")
        
        val extension = fileName.substringAfterLast(".", "").lowercase()
        
        // 根据文件类型选择不同的预览方式
        when {
            isPdfFile(extension) -> {
                Log.d("MessageAdapter", "Starting FilePreviewActivity for PDF")
                try {
                    val intent = Intent(context, FilePreviewActivity::class.java).apply {
                        putExtra("fileUrl", fullUrl)
                        putExtra("fileName", fileName)
                        putExtra("fileType", extension)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MessageAdapter", "Error starting FilePreviewActivity: ${e.message}", e)
                    Toast.makeText(context, "无法打开文件预览: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            isImageFile(extension) -> {
                showImagePreview(fileUrl)
            }
            isVideoFile(extension) -> {
                showVideoPreview(fileUrl)
            }
            isWordFile(extension) || isExcelFile(extension) || isPptFile(extension) -> {
                val intent = Intent(context, FilePreviewActivity::class.java).apply {
                    putExtra("fileUrl", fullUrl)
                    putExtra("fileName", fileName)
                    putExtra("fileType", extension)
                }
                context.startActivity(intent)
            }
            else -> {
                // 其他类型的文件直接下载
                openFile(fullUrl)
            }
        }
    }

    private fun saveImageToGallery(context: Context, imageUrl: String) {
        // 启动协程在后台下载图片
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 从网络获取图片
                val url = if (imageUrl.startsWith("http")) {
                    imageUrl
                } else {
                    "${getBaseUrl(context)}$imageUrl"
                }
                
                Log.d("MessageAdapter", "Downloading image from: $url")
                
                // 使用 Glide 下载图片
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
                
                // 创建文件名
                val fileName = "IMG_${System.currentTimeMillis()}.jpg"
                
                // 保存图片到相册
                withContext(Dispatchers.Main) {
                    saveImageToGallery(context, bitmap, fileName)
                }
            } catch (e: Exception) {
                Log.e("MessageAdapter", "Error downloading image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            // 根据 Android 版本选择不同的保存方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 及以上使用 MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    
                    Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(context, "保存图片失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 9 及以下使用传统方法
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                val imageFile = File(imagesDir, fileName)
                val outputStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()
                
                // 通知相册更新
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = AndroidUri.fromFile(imageFile)
                context.sendBroadcast(mediaScanIntent)
                
                Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MessageAdapter", "Error saving image: ${e.message}", e)
            Toast.makeText(context, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 在 Adapter 销毁时移除所有待执行的延迟任务
    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
    }
}