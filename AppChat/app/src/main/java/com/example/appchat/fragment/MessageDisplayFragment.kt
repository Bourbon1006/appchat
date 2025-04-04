package com.example.appchat.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.appchat.R
import com.example.appchat.activity.ChatActivity
import com.example.appchat.adapter.MessageSessionAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentMessageDisplayBinding
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageSession
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch
import java.time.LocalDateTime

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

    private val sessionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.appchat.UPDATE_CHAT_SESSIONS") {
                // 重新加载会话列表
                loadSessions()
            }
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
        // 记录当前点击的会话信息，用于调试
        Log.d("MessageDisplayFragment", "Navigating to chat: type=${session.type}, partnerId=${session.partnerId}, name=${session.partnerName}")
        
        startActivity(Intent(context, ChatActivity::class.java).apply {
            when (session.type?.uppercase() ?: "PRIVATE") {
                "GROUP" -> {
                    putExtra("chat_type", "GROUP")
                    putExtra("receiver_id", session.partnerId)  // 使用统一的 receiver_id
                    putExtra("receiver_name", session.partnerName)
                }
                else -> {
                    putExtra("chat_type", "PRIVATE")
                    putExtra("receiver_id", session.partnerId)  // 使用统一的 receiver_id
                    putExtra("receiver_name", session.partnerName)
                }
            }
            
            // 打印日志确认参数
            println("🚀 Starting ChatActivity with:")
            println("   chat_type: ${getStringExtra("chat_type")}")
            println("   receiver_id: ${getLongExtra("receiver_id", -1)}")
            println("   receiver_name: ${getStringExtra("receiver_name")}")
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

        // 注册广播接收器
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                sessionUpdateReceiver,
                IntentFilter("com.example.appchat.UPDATE_CHAT_SESSIONS")
            )
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
                // 使用安全的方式显示Toast
                activity?.let { activity ->
                    if (!activity.isFinishing && isAdded) {
                        Toast.makeText(activity, "加载会话列表失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                if (isAdded && view != null) {
                    swipeRefreshLayout.isRefreshing = false
                }
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
        // 注销广播接收器
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(sessionUpdateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _binding = null
        // 清除引用，避免内存泄漏
        WebSocketManager.setMessageDisplayFragment(null)
        // 移除监听器
        WebSocketManager.removeSessionUpdateListener(sessionUpdateListener)
        WebSocketManager.removeFriendDeletedListener(friendDeletedListener)
    }

    private fun updateSessionWithNewMessage(message: ChatMessage) {
        if (!isAdded) return  // 如果Fragment已经分离，则直接返回
        
        val currentUserId = UserPreferences.getUserId(requireContext())
        
        // 判断消息类型（私聊/群聊）和会话ID
        val sessionId: Long = when {
            message.groupId != null -> message.groupId
            else -> if (message.senderId == currentUserId) message.receiverId ?: 0L else message.senderId
        }
        
        // 确定聊天类型
        val chatType = if (message.groupId != null) "GROUP" else "PRIVATE"
        
        // 获取会话名称
        val sessionName: String = when (chatType) {
            "GROUP" -> message.groupName ?: "未知群组"
            else -> if (message.senderId == currentUserId) 
                message.receiverName ?: "未知用户" 
            else
                message.senderName
        }
        
        // 查找现有会话
        val existingSession = sessions.find { 
            when (chatType) {
                "GROUP" -> it.type == "GROUP" && it.partnerId == sessionId
                else -> it.type == "PRIVATE" && it.partnerId == sessionId
            }
        }
        
        if (existingSession != null) {
            // 更新现有会话
            val updatedSession = message.timestamp?.let {
                existingSession.copy(
                    lastMessage = message.content,
                    lastMessageTime = it.toString(),
                    unreadCount = existingSession.unreadCount + when {
                        // 如果是自己发送的消息，不增加未读数
                        message.senderId == currentUserId -> 0
                        // 如果当前正在查看这个会话，不增加未读数
                        isCurrentlyViewing(sessionId, chatType) -> 0
                        // 其他情况增加未读数
                        else -> 1
                    }
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
                    id = sessionId,
                    partnerId = sessionId,
                    partnerName = sessionName,
                    partnerAvatar = null,
                    lastMessage = message.content,
                    lastMessageTime = timestamp.toString(),
                    unreadCount = if (message.senderId == currentUserId) 0 else 1,
                    type = chatType
                )
                // 将新会话添加到列表开头
                sessions.add(0, newSession)
            }
        }
        
        // 通知适配器更新
        adapter.updateSessions(sessions)
    }

    // 判断是否正在查看该会话
    private fun isCurrentlyViewing(sessionId: Long, chatType: String): Boolean {
        val activity = requireActivity()
        val currentFragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer)
        
        // 检查当前Activity是否是ChatActivity
        if (activity is ChatActivity) {
            return when (chatType) {
                "GROUP" -> activity.getCurrentGroupId() == sessionId
                else -> activity.getCurrentPartnerId() == sessionId
            }
        }
        return false
    }
}