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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.MediaScannerConnection
import com.example.appchat.api.ApiClient

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
    private var itemLongClickListener: ((Int) -> Boolean)? = null

    fun setOnItemLongClickListener(listener: (Int) -> Boolean) {
        itemLongClickListener = listener
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

    fun toggleMessageSelection(messageId: Long) {
        if (selectedMessages.contains(messageId)) {
            selectedMessages.remove(messageId)
        } else {
            selectedMessages.add(messageId)
        }
        val position = messages.indexOfFirst { it.id == messageId }
        if (position != -1) {
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
        // 确保消息的聊天类型正确
        val updatedMessage = message.copy(
            chatType = currentChatType,
            receiverId = when (currentChatType) {
                "GROUP" -> null
                else -> if (message.senderId == currentUserId) chatPartnerId else currentUserId
            }
        )
        messages.add(updatedMessage)
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
        val iterator = messages.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            if (message.id == messageId) {
                iterator.remove()
                break
            }
        }
        notifyDataSetChanged() // 确保 UI 更新
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
        messages.addAll(newMessages.map { message ->
            message.copy(
                chatType = currentChatType,  // 确保使用正确的聊天类型
                receiverId = when (currentChatType) {
                    "GROUP" -> null
                    else -> if (message.senderId == currentUserId) chatPartnerId else currentUserId
                }
            )
        })
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

    fun getSelectedMessages(): List<Long> {
        return selectedMessages.toList()
    }

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

    fun getMessage(messageId: Long): ChatMessage? {
        return messages.find { it.id == messageId }
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
        holder.bind(message, previousMessage)  // 调用 ViewHolder 的 bind 方法
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
        private val checkbox: CheckBox = itemView.findViewById(R.id.messageCheckbox)
            ?: throw IllegalStateException("Required view 'messageCheckbox' not found")
        private val senderName: TextView = itemView.findViewById(R.id.senderName)

        fun bind(message: ChatMessage, previousMessage: ChatMessage?) {
            // 构建基础 URL
            val baseUrl = with(itemView.context) {
                val ip = getString(R.string.server_ip)
                val port = getString(R.string.server_port)
                getString(R.string.server_url_format).format(ip, port)
            }.trimEnd('/')

            // 加载头像
            val avatarUrl = "$baseUrl/api/users/${message.senderId}/avatar"
            println("⭐ Loading avatar from URL: $avatarUrl")

            if (!itemView.context.isDestroyed()) {
                Glide.with(itemView.context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.loading_placeholder)
                    .error(R.drawable.error_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(avatarImage)
            }

            // 处理消息内容
            when (message.type) {
                MessageType.TIME -> handleTimeMessage(message)
                MessageType.TEXT -> handleTextMessage(message)
                MessageType.FILE -> handleFileMessage(message, baseUrl)
                MessageType.IMAGE -> handleImageMessage(message)
                MessageType.VIDEO -> handleVideoMessage(message)
            }

            // 在群聊中显示发送者名称（除了自己发的消息）
            senderName.apply {
                if (message.chatType == "GROUP" && message.senderId != currentUserId) {
                    visibility = View.VISIBLE
                    text = message.senderName
                } else {
                    visibility = View.GONE
                }
            }

            // 添加长按事件监听
            itemView.setOnLongClickListener {
                message.id?.let { messageId ->
                    if (!isMultiSelectMode) {
                        itemLongClickListener?.invoke(adapterPosition)
                    }
                }
                true
            }

            // 添加点击事件监听
            itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    message.id?.let { messageId -> toggleMessageSelection(messageId) }
                }
            }

            // 设置多选模式
            checkbox.apply {
                visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
                isChecked = selectedMessages.contains(message.id)
                setOnClickListener {
                    message.id?.let { messageId -> toggleMessageSelection(messageId) }
                }
            }

            // 处理高亮显示
            if (adapterPosition == highlightedPosition) {
                itemView.setBackgroundResource(R.color.search_highlight)  // 添加这个高亮背景色
                // 3秒后取消高亮
                Handler(Looper.getMainLooper()).postDelayed({
                    highlightedPosition = -1
                    itemView.setBackgroundResource(android.R.color.transparent)
                }, 3000)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
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

        private fun handleFileMessage(message: ChatMessage, baseUrl: String) {
            val extension = message.content.substringAfterLast('.', "").lowercase()
            when {
                isImageFile(extension) -> {
                    messageText.visibility = View.GONE
                    fileContainer?.visibility = View.VISIBLE
                    fileIcon?.visibility = View.VISIBLE
                    playIcon?.visibility = View.GONE

                    // 检查并构建正确的图片 URL
                    val imageUrl = message.fileUrl?.let { fileUrl ->
                        if (fileUrl.startsWith("http")) fileUrl else "$baseUrl$fileUrl"
                    }

                    // 只有在 URL 不为空时才加载图片
                    imageUrl?.let { url ->
                        println("🖼️ Loading image from URL: $url")
                        fileIcon?.let { imageView ->
                            Glide.with(itemView.context)
                                .load(url)
                                .apply(RequestOptions()
                                    .override(800, 800)
                                    .centerInside()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .placeholder(R.drawable.ic_image_loading)
                                    .error(R.drawable.ic_image_error))
                                .into(imageView)
                        }

                        // 点击查看大图
                        fileContainer?.setOnClickListener {
                            val intent = Intent(context, ImagePreviewActivity::class.java).apply {
                                putExtra("imageUrl", url)
                            }
                            context.startActivity(intent)
                        }

                        // 直接使用已有的 showFileOptions 方法
                        fileContainer?.setOnLongClickListener {
                            showFileOptions(itemView.context, message, url)
                            true
                        }
                    } ?: run {
                        // 如果 URL 为空，显示错误占位图
                        fileIcon?.setImageResource(R.drawable.ic_image_error)
                        println("⚠️ Image URL is null for message: ${message.id}")
                    }
                }
                isVideoFile(extension) -> {
                    messageText.visibility = View.GONE
                    fileContainer?.visibility = View.VISIBLE
                    fileIcon?.visibility = View.VISIBLE
                    playIcon?.visibility = View.VISIBLE

                    // 加载视频缩略图
                    val videoUrl = message.fileUrl?.let { fileUrl ->
                        if (fileUrl.startsWith("http")) fileUrl else "$baseUrl$fileUrl"
                    }

                    videoUrl?.let { url ->
                        Glide.with(itemView.context)
                            .load(url)
                            .placeholder(R.drawable.video_placeholder)
                            .error(R.drawable.video_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(fileIcon!!)

                        // 设置点击事件打开视频预览
                        fileContainer?.setOnClickListener {
                            val intent = Intent(itemView.context, VideoPreviewActivity::class.java)
                            intent.putExtra("videoUrl", url)
                            itemView.context.startActivity(intent)
                        }

                        // 直接使用已有的 showFileOptions 方法
                        fileContainer?.setOnLongClickListener {
                            showFileOptions(itemView.context, message, url)
                            true
                        }
                    }
                }
                else -> {
                    // 其他类型文件的处理
                    messageText.visibility = View.VISIBLE
                    fileContainer?.visibility = View.VISIBLE
                    fileIcon?.visibility = View.VISIBLE
                    playIcon?.visibility = View.GONE

                    when {
                        isPdfFile(extension) -> {
                            messageText.text = "📄 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_pdf)
                        }
                        isWordFile(extension) -> {
                            messageText.text = "📝 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_word)
                        }
                        else -> {
                            messageText.text = "📎 ${message.content}"
                            fileIcon?.setImageResource(R.drawable.ic_file)
                        }
                    }

                    // 直接使用已有的 showFileOptions 方法
                    fileContainer?.setOnLongClickListener {
                        val fileUrl = message.fileUrl?.let { url ->
                            if (url.startsWith("http")) url else "$baseUrl$url"
                        }
                        fileUrl?.let { url ->
                            showFileOptions(itemView.context, message, url)
                        }
                        true
                    }
                }
            }
        }

        private fun handleImageMessage(message: ChatMessage) {
            messageText.text = message.content
            fileIcon?.visibility = View.GONE
            fileContainer?.setOnClickListener(null)
        }

        private fun handleVideoMessage(message: ChatMessage) {
            messageText.text = message.content
            fileIcon?.visibility = View.GONE
            fileContainer?.setOnClickListener(null)
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
        return extension == "pdf"
    }

    private fun isWordFile(extension: String): Boolean {
        return extension in listOf("doc", "docx", "txt")
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

    private fun showFileOptions(context: Context, message: ChatMessage, fileUrl: String) {
        val items = arrayOf("保存到手机", "转发", "删除")
        AlertDialog.Builder(context)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // 使用 DownloadManager 保存文件
                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val request = DownloadManager.Request(Uri.parse(fileUrl))
                            .setTitle(message.content)
                            .setDescription("正在下载文件...")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, message.content)
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(true)

                        try {
                            val downloadId = downloadManager.enqueue(request)
                            Toast.makeText(context, "开始下载...", Toast.LENGTH_SHORT).show()

                            // 注册下载完成的广播接收器
                            val onComplete = object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                    if (id == downloadId) {
                                        Toast.makeText(context, "下载完成！已保存到下载目录", Toast.LENGTH_SHORT).show()
                                        context.unregisterReceiver(this)
                                    }
                                }
                            }
                            context.registerReceiver(
                                onComplete,
                                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        // TODO: 实现转发功能
                        Toast.makeText(context, "转发功能开发中...", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 删除消息确认
                        AlertDialog.Builder(context)
                            .setTitle("删除消息")
                            .setMessage("确定要删除这条消息吗？")
                            .setPositiveButton("删除") { _, _ ->
                                message.id?.let { messageId -> onMessageDelete(messageId) }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .show()
    }
}