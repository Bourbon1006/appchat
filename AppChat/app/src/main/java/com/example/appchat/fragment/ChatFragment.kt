package com.example.appchat.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.MainActivity
import com.example.appchat.R
import com.example.appchat.adapter.MessageAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.db.ChatDatabase
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDateTime

class ChatFragment : Fragment() {
    private lateinit var messagesList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var adapter: MessageAdapter
    private var receiverId: Long = 0
    private var receiverName: String = ""
    private var isMultiSelectMode = false

    companion object {
        private const val ARG_RECEIVER_ID = "receiver_id"
        private const val ARG_RECEIVER_NAME = "receiver_name"

        fun newInstance(receiverId: Long, receiverName: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RECEIVER_ID, receiverId)
                    putString(ARG_RECEIVER_NAME, receiverName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            receiverId = it.getLong(ARG_RECEIVER_ID)
            receiverName = it.getString(ARG_RECEIVER_NAME) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        messagesList = view.findViewById(R.id.messagesList)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)

        setupRecyclerView()
        setupSendButton()
        loadChatHistory()
        setupWebSocket()

        return view
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            context = requireContext(),
            currentUserId = UserPreferences.getUserId(requireContext()),
            currentChatType = "private",
            chatPartnerId = receiverId
        ) { messageId ->
            if (isMultiSelectMode) {
                // 多选模式下，点击消息进行选择/取消选择
                adapter.toggleMessageSelection(messageId)
            } else {
                // 非多选模式下，点击消息进行删除
                deleteMessage(messageId)
            }
        }
        
        // 设置布局管理器
        messagesList.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        
        // 直接设置适配器
        messagesList.adapter = adapter

        // 添加长按事件监听，只进入多选模式，不立即显示删除对话框
        adapter.setOnItemLongClickListener { position ->
            val messageId = adapter.getItemId(position)
            if (!isMultiSelectMode) {
                enterMultiSelectMode() // 进入多选模式
                adapter.toggleMessageSelection(messageId) // 选中长按的消息
                
                // 显示删除按钮或工具栏
                (activity as? MainActivity)?.let { mainActivity ->
                    mainActivity.showDeleteButton { 
                        // 点击删除按钮时的回调
                        showDeleteMessagesDialog()
                    }
                }
            }
            true
        }
    }

    // 添加进入多选模式的方法
    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        adapter.enterMultiSelectMode()
        
        // 通知 MainActivity 进入多选模式
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.showMultiSelectActionBar()
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val content = messageInput.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
                messageInput.text.clear()
            }
        }
    }

    private fun loadChatHistory() {
        ApiClient.apiService.getPrivateMessages(UserPreferences.getUserId(requireContext()), receiverId)
            .enqueue(object : Callback<List<ChatMessage>> {
                override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { messages ->
                            adapter.updateMessages(messages)
                            messagesList.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }

                override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                    Toast.makeText(context, "加载聊天记录失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupWebSocket() {
        WebSocketManager.addMessageListener { message ->
            if (message.senderId == receiverId || message.receiverId == receiverId) {
                activity?.runOnUiThread {
                    adapter.addMessage(message)
                    messagesList.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private fun sendMessage(content: String) {
        val message = ChatMessage(
            content = content,
            senderId = UserPreferences.getUserId(requireContext()),
            senderName = UserPreferences.getUsername(requireContext()),
            receiverId = receiverId,
            receiverName = receiverName,
            type = MessageType.TEXT,
            timestamp = LocalDateTime.now()
        )

        WebSocketManager.sendMessage(message)
    }

    private fun showDeleteMessagesDialog() {
        val selectedMessages = adapter.getSelectedMessages()
        if (selectedMessages.isEmpty()) {
            Toast.makeText(context, "请选择要删除的消息", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("删除消息")
            .setMessage("确定要删除选中的 ${selectedMessages.size} 条消息吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteSelectedMessages()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSelectedMessages() {
        lifecycleScope.launch {
            try {
                adapter.getSelectedMessages().forEach { messageId ->
                    val response = ApiClient.apiService.deleteMessage(
                        messageId,
                        UserPreferences.getUserId(requireContext())
                    )
                    if (response.isSuccessful) {
                        adapter.removeMessageCompletely(messageId)
                        }
                    }

                exitMultiSelectMode()
                Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteMessage(messageId: Long) {
        lifecycleScope.launch {
            try {
                println("🗑️ Starting message deletion process: $messageId")

                // 从适配器中完全删除消息
                adapter.removeMessageCompletely(messageId) // 调用 removeMessageCompletely 方法
                println("✅ Local message deletion completed")

                // 从服务器删除消息
                val response = ApiClient.apiService.deleteMessage(
                    messageId,
                    UserPreferences.getUserId(requireContext())
                )
                if (response.isSuccessful) {
                    println("✅ Server message deletion successful")
                    Toast.makeText(requireContext(), "消息已删除", Toast.LENGTH_SHORT).show()
                } else {
                    println("⚠️ Server deletion failed but local deletion succeeded: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error in deletion process: ${e.message}")
            }
        }
    }

    // 添加退出多选模式的方法
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.exitMultiSelectMode()
        
        // 通知 MainActivity 退出多选模式
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.hideMultiSelectActionBar()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        WebSocketManager.removeMessageListeners()
    }
}