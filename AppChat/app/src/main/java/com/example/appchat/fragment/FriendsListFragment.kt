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
import com.example.appchat.ChatActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Contact
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch

class FriendsListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_friends_list, container, false)
        recyclerView = view.findViewById(R.id.friendsRecyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        loadFriends()
        
        // 注册 WebSocket 监听器，接收联系人状态更新
        WebSocketManager.addOnlineStatusListener { userId, status ->
            // 在联系人列表中查找对应的联系人，并更新其状态
            val updatedContacts = contacts.map { contact ->
                if (contact.id == userId) {
                    contact.copy(onlineStatus = status)
                } else {
                    contact
                }
            }
            
            // 更新适配器
            activity?.runOnUiThread {
                contacts.clear()
                contacts.addAll(updatedContacts)
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ContactAdapter(contacts) { contact ->
            // 处理联系人点击事件
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("chat_type", "PRIVATE")  // 使用 ChatActivity 期望的参数名
                putExtra("receiver_id", contact.id)  // 使用 ChatActivity 期望的参数名
                putExtra("receiver_name", contact.nickname ?: contact.username)  // 使用 ChatActivity 期望的参数名
            }
            startActivity(intent)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FriendsListFragment.adapter
        }
    }
    
    fun refreshFriendsList() {
        loadFriends()
    }
    
    private fun loadFriends() {
        val userId = UserPreferences.getUserId(requireContext())
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriends(userId)
                if (response.isSuccessful) {
                    response.body()?.let { userList ->
                        // 将 UserDTO 转换为 Contact
                        val contactList = userList.map { user ->
                            Contact(
                                id = user.id,
                                username = user.username,
                                nickname = user.nickname,
                                avatarUrl = user.avatarUrl,
                                onlineStatus = user.onlineStatus ?: 0
                            )
                        }
                        contacts.clear()
                        contacts.addAll(contactList)
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(context, "加载好友列表失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 移除 WebSocket 监听器
        WebSocketManager.removeOnlineStatusListener()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次恢复时重新加载好友列表
        loadFriends()
    }
} 