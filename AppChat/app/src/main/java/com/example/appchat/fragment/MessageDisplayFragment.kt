package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.appchat.ChatActivity
import com.example.appchat.R
import com.example.appchat.adapter.MessageSessionAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentMessageDisplayBinding
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageSession
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageDisplayFragment : Fragment() {
    private var _binding: FragmentMessageDisplayBinding? = null
    private val binding get() = _binding!!
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageSessionAdapter
    private val sessions = mutableListOf<MessageSession>()
    
    private val sessionUpdateListener: (ChatMessage) -> Unit = { message ->
        // 在主线程中更新UI
        activity?.runOnUiThread {
            // 更新会话列表
            updateSessionWithNewMessage(message)
        }
    }

    private val friendDeletedListener: (Long) -> Unit = { friendId ->
        // 从会话列表中移除该好友的会话
        val sessionToRemove = sessions.find { 
            it.type == "PRIVATE" && it.partnerId == friendId 
        }
        sessionToRemove?.let {
            sessions.remove(it)
            adapter.updateSessions(sessions)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageDisplayBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = MessageSessionAdapter(
            onItemClick = { session -> navigateToChat(session) },
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner
        )
        
        adapter.updateSessions(sessions)
        
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshSessions()
        }
    }

    private fun refreshSessions() {
        // 实现刷新逻辑
    }

    private fun navigateToChat(session: MessageSession) {
        startActivity(Intent(context, ChatActivity::class.java).apply {
            when (session.type.uppercase()) {
                "GROUP" -> {
                    putExtra("chat_type", "GROUP")
                    putExtra("group_id", session.partnerId)
                    putExtra("group_name", session.partnerName)
                }
                else -> {
                    putExtra("chat_type", "PRIVATE")
                    putExtra("receiver_id", session.partnerId)
                    putExtra("receiver_name", session.partnerName)
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        _binding = FragmentMessageDisplayBinding.bind(view)
        swipeRefreshLayout = binding.swipeRefreshLayout
        recyclerView = binding.recyclerView

        setupRecyclerView()
        setupSwipeRefresh()

        println("🔄 MessageDisplayFragment: onViewCreated")
        // 首次加载数据
        loadSessions()

        // 设置 WebSocketManager 的引用
        WebSocketManager.setMessageDisplayFragment(this)

        // 注册会话更新监听器
        WebSocketManager.addSessionUpdateListener(sessionUpdateListener)
        WebSocketManager.addFriendDeletedListener(friendDeletedListener)
    }

    private fun loadSessions() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📥 Loading message sessions for user: $userId")
        lifecycleScope.launch {
            try {
                val sessions = ApiClient.apiService.getMessageSessions(userId)
                println("📦 Loaded ${sessions.size} sessions")
                updateSessions(sessions)
            } catch (e: Exception) {
                println("❌ Failed to load sessions: ${e.message}")
                Toast.makeText(context, "加载会话列表失败", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    fun updateSessions(newSessions: List<MessageSession>) {
        // 更新会话列表
        sessions.clear()
        sessions.addAll(newSessions)
        adapter.updateSessions(sessions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 清除引用，避免内存泄漏
        WebSocketManager.setMessageDisplayFragment(null)
        // 移除监听器
        WebSocketManager.removeSessionUpdateListener(sessionUpdateListener)
        WebSocketManager.removeFriendDeletedListener(friendDeletedListener)
    }

    private fun updateSessionWithNewMessage(message: ChatMessage) {
        // 判断消息类型（私聊/群聊）
        val sessionId: Long = when {
            message.groupId != null -> message.groupId
            message.senderId == UserPreferences.getUserId(requireContext()) -> message.receiverId ?: 0L
            else -> message.senderId ?: 0L
        }
        
        // 获取会话名称
        val sessionName: String = when {
            message.groupId != null -> (ApiClient.apiService.getGroupById(message.groupId) ?: "未知群组").toString()
            message.senderId == UserPreferences.getUserId(requireContext()) -> message.receiverName ?: "未知用户"
            else -> message.senderName ?: "未知用户"
        }
        
        // 查找现有会话
        val existingSession = sessions.find { it.partnerId == sessionId }
        
        if (existingSession != null) {
            // 更新现有会话
            val updatedSession = message.timestamp?.let {
                existingSession.copy(
                    lastMessage = message.content,
                    lastMessageTime = it,
                    unreadCount = existingSession.unreadCount +
                        if (message.senderId != UserPreferences.getUserId(requireContext())) 1 else 0
                )
            }
            
            // 从列表中移除旧会话
            sessions.remove(existingSession)
            // 将更新后的会话添加到列表开头
            updatedSession?.let { sessions.add(0, it) }
        } else {
            // 创建新会话
            message.timestamp?.let { timestamp ->
                val newSession = MessageSession(
                    id = 0, // 临时ID，服务器会分配真实ID
                    partnerId = sessionId,
                    partnerName = sessionName,
                    lastMessage = message.content,
                    lastMessageTime = timestamp,
                    unreadCount = 1,
                    type = if (message.groupId != null) "GROUP" else "PRIVATE",
                    partnerAvatar = null // 可以后续更新头像
                )
                // 将新会话添加到列表开头
                sessions.add(0, newSession)
            }
        }
        
        // 通知适配器更新
        adapter.updateSessions(sessions)
    }
} 