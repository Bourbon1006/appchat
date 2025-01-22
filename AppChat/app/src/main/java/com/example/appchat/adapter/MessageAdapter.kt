package com.example.appchat.adapter

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageAdapter(private val currentUserId: Long) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_MY_MESSAGE) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        
        return MessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
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

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView? = itemView.findViewById(R.id.nameText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView? = itemView.findViewById(R.id.timeText)
        private val fileIcon: ImageView? = itemView.findViewById(R.id.fileIcon)
        private val fileContainer: View? = itemView.findViewById(R.id.fileContainer)
        
        fun bind(message: ChatMessage) {
            nameText?.text = message.senderName
            
            when (message.type) {
                MessageType.TEXT -> {
                    messageText.text = message.content
                    fileIcon?.visibility = View.GONE
                    fileContainer?.setOnClickListener(null)
                }
                MessageType.FILE -> {
                    println("Binding file message: ${message.content}")
                    messageText.text = "📎 ${message.content}"
                    fileIcon?.visibility = View.VISIBLE
                    fileContainer?.let { container ->
                        println("Setting click listener on container")
                        container.setOnClickListener {
                            println("File container clicked")
                            message.fileUrl?.let { url -> 
                                println("Starting download for URL: $url")
                                downloadFile(url, message.content)
                            } ?: run {
                                Toast.makeText(itemView.context, "文件链接无效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } ?: println("File container is null")
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
            }
            
            timeText?.text = message.timestamp?.let { formatTime(it) } ?: ""
        }

        private fun downloadFile(url: String, filename: String) {
            try {
                println("Starting download: $url")
                // 创建下载目录
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "chat_files"
                ).apply { mkdirs() }

                // 确保文件名是唯一的
                val file = File(downloadDir, filename).let { baseFile ->
                    var index = 0
                    var currentFile = baseFile
                    while (currentFile.exists()) {
                        index++
                        val name = baseFile.nameWithoutExtension
                        val ext = baseFile.extension
                        currentFile = File(downloadDir, "${name}_${index}.${ext}")
                    }
                    currentFile
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
                downloadManager.enqueue(request)
                
                Toast.makeText(itemView.context, "开始下载: $filename", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                println("Download error: ${e.message}")
                e.printStackTrace()
                Toast.makeText(itemView.context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun getMimeType(filename: String): String {
            return when (filename.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
        }

        private fun formatTime(timestamp: LocalDateTime): String {
            return try {
                DateTimeFormatter.ofPattern("HH:mm").format(timestamp)
            } catch (e: Exception) {
                ""
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
    }
} 