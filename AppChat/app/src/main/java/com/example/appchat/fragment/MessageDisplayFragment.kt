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
import com.example.appchat.ChatActivity
import com.example.appchat.R
import com.example.appchat.adapter.MessageSessionAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.MessageSession
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch

class MessageDisplayFragment : Fragment() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageSessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_message_display, container, false)
        
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        recyclerView = view.findViewById(R.id.recyclerView)
        
        setupRecyclerView()
        setupSwipeRefresh()
        
        return view
    }

    private fun setupRecyclerView() {
        adapter = MessageSessionAdapter(
            onItemClick = { session -> navigateToChat(session) },
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshMessages()
        }
    }

    private fun refreshMessages() {
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
        
        // 首次加载数据
        loadMessageSessions()

        // 添加 WebSocket 消息监听
        setupWebSocketListener()
    }

    private fun loadMessageSessions() {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                // 从服务器获取最新的会话列表
                val sessions = ApiClient.apiService.getMessageSessions(userId)
                adapter.updateSessions(sessions)
            } catch (e: Exception) {
                Toast.makeText(context, "加载会话列表失败", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupWebSocketListener() {
        WebSocketManager.addMessageListener { message ->
            // 收到新消息时刷新会话列表
            loadMessageSessions()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 移除 WebSocket 消息监听
        WebSocketManager.removeMessageListener { }
    }
} 