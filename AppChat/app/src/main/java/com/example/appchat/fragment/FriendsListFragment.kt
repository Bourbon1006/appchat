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
import com.example.appchat.activity.ChatActivity
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentFriendsListBinding
import com.example.appchat.model.Contact
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import kotlinx.coroutines.launch

class FriendsListFragment : Fragment() {
    private var _binding: FragmentFriendsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var contactAdapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContactList()
        loadContacts()
        setupWebSocketListener()
    }
    
    private fun setupWebSocketListener() {
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
                contactAdapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun setupContactList() {
        contactAdapter = ContactAdapter(contacts) { contact ->
            // 确保contact.id有效
            if (contact.id <= 0) {
                println("❌ Invalid contact ID: ${contact.id}")
                Toast.makeText(context, "无效的联系人ID", Toast.LENGTH_SHORT).show()
                return@ContactAdapter
            }
            
            println("📱 Contact clicked: id=${contact.id}, name=${contact.nickname ?: contact.username}")
            
            // 创建Intent并明确设置所有必要的参数
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("chat_type", "PRIVATE")
                putExtra("receiver_id", contact.id)
                putExtra("receiver_name", contact.nickname ?: contact.username)
                // 记录当前用户ID，以便调试
                putExtra("current_user_id", UserPreferences.getUserId(requireContext()))
            }
            
            println("🚀 Starting ChatActivity with intent extras:")
            intent.extras?.keySet()?.forEach { key ->
                println("   $key = ${intent.extras?.get(key)}")
            }
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                println("❌ Failed to start ChatActivity: ${e.message}")
                e.printStackTrace()
                Toast.makeText(requireContext(), "无法打开聊天界面: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.contactsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
        }
    }
    
    fun refreshContacts() {
        println("📝 FriendsListFragment.refreshContacts() called")
        loadContacts()
    }
    
    private fun loadContacts() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📝 FriendsListFragment.loadContacts() for userId: $userId")
        
        lifecycleScope.launch {
            try {
                println("🔄 Making API call to load contacts")
                val response = ApiClient.apiService.getFriends(userId)
                println("📡 Got response: ${response.isSuccessful}, code: ${response.code()}")
                
                if (response.isSuccessful) {
                    response.body()?.let { userList ->
                        println("✅ FriendsListFragment loaded ${userList.size} contacts")
                        activity?.runOnUiThread {
                            println("🔄 Updating contacts adapter with ${userList.size} items")
                            contacts.clear()
                            contacts.addAll(userList.map { user ->
                                Contact(
                                    id = user.id,
                                    username = user.username,
                                    nickname = user.nickname,
                                    avatarUrl = user.avatarUrl,
                                    onlineStatus = user.onlineStatus ?: 0
                                )
                            })
                            contactAdapter.notifyDataSetChanged()
                            println("✅ Adapter notified of changes")
                        }
                    }
                } else {
                    println("❌ Failed to load contacts: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Error loading contacts: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 移除 WebSocket 监听器
        WebSocketManager.removeOnlineStatusListener()
    }
    
    override fun onResume() {
        super.onResume()
        loadContacts()
    }
} 