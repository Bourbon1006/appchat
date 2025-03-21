package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.appchat.ChatActivity
import com.example.appchat.MainActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Contact
import com.example.appchat.model.UserDTO
import com.example.appchat.model.FriendRequest
import com.example.appchat.util.UserPreferences
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.appchat.databinding.FragmentContactsBinding
import com.example.appchat.websocket.WebSocketManager
import com.example.appchat.dialog.FriendRequestsDialog
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class ContactsFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var binding: FragmentContactsBinding
    private var pendingRequestCount = 0
    
    // 添加联系人适配器
    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var contactAdapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    // 添加待处理请求数量监听器
    private val pendingRequestCountListener: (Int) -> Unit = { count ->
        if (isAdded) {
            pendingRequestCount = count
            updateFriendRequestBadge()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册待处理请求数量监听器
        WebSocketManager.addPendingRequestCountListener(pendingRequestCountListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentContactsBinding.inflate(inflater, container, false)
        
        viewPager = binding.viewPager
        tabLayout = binding.tabLayout
        
        setupViewPager()
        setupFriendRequestSection()
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPendingRequests()
        
        // 设置联系人列表
        setupContactsList()
        
        // 加载联系人列表
        loadContacts()
        
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
    
    private fun setupContactsList() {
        contactsRecyclerView = binding.contactsRecyclerView ?: return
        contactAdapter = ContactAdapter(contacts) { contact ->
            // 处理联系人点击事件
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("chat_type", "PRIVATE")
                putExtra("receiver_id", contact.id)
                putExtra("receiver_name", contact.nickname ?: contact.username)
            }
            startActivity(intent)
        }
        
        contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
        }
    }
    
    private fun loadContacts() {
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
                        contactAdapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(requireContext(), "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViewPager() {
        val adapter = ContactsPagerAdapter(this)
        viewPager.adapter = adapter
        
        // 连接 TabLayout 和 ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "好友"
                1 -> "群组"
                else -> ""
            }
        }.attach()
    }

    private fun setupFriendRequestSection() {
        binding.friendRequestSection.setOnClickListener {
            // 移除检查，允许随时查看好友请求列表
            showFriendRequestsDialog()
        }

        // 注册好友请求监听器
        WebSocketManager.addFriendRequestListener(friendRequestListener)
        WebSocketManager.addFriendRequestResultListener(friendRequestResultListener)
    }

    private fun updateFriendRequestBadge() {
        if (isAdded) {  // 确保 Fragment 已附加到 Activity
            // 更新好友请求入口的角标
            binding.friendRequestBadge.apply {
                visibility = if (pendingRequestCount > 0) View.VISIBLE else View.GONE
                text = pendingRequestCount.toString()
            }

            // 更新底部导航栏的角标
            val bottomNav = (activity as? MainActivity)?.binding?.bottomNavigation
            val badge = bottomNav?.getOrCreateBadge(R.id.navigation_contacts)
            if (pendingRequestCount > 0) {
                badge?.apply {
                    isVisible = true
                    backgroundColor = ContextCompat.getColor(requireContext(), R.color.red)
                    number = pendingRequestCount
                }
            } else {
                badge?.isVisible = false
            }
        }
    }

    private fun showFriendRequestsDialog() {
        if (isAdded) {  // 确保 Fragment 已附加到 Activity
            val dialog = FriendRequestsDialog()
            dialog.show(childFragmentManager, "FriendRequestsDialog")
        }
    }

    fun loadPendingRequests() {
        lifecycleScope.launch {
            try {
                val userId = UserPreferences.getUserId(requireContext())
                val response = ApiClient.apiService.getPendingRequests(userId)
                if (response.isSuccessful) {
                    response.body()?.let { requests ->
                        pendingRequestCount = requests.size
                        updateFriendRequestBadge()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载好友请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 只有在没有待处理请求时才清除角标
        if (pendingRequestCount == 0) {
            val bottomNav = (activity as? MainActivity)?.binding?.bottomNavigation
            bottomNav?.removeBadge(R.id.navigation_contacts)
        }
        
        // 移除监听器
        WebSocketManager.removeFriendRequestListener(friendRequestListener)
        WebSocketManager.removeFriendRequestResultListener(friendRequestResultListener)
        
        // 移除 WebSocket 监听器
        WebSocketManager.removeOnlineStatusListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除待处理请求数量监听器
        WebSocketManager.removePendingRequestCountListener(pendingRequestCountListener)
        if (pendingRequestCount == 0) {
            val bottomNav = (activity as? MainActivity)?.binding?.bottomNavigation
            bottomNav?.removeBadge(R.id.navigation_contacts)
        }
    }

    override fun onResume() {
        super.onResume()
        WebSocketManager.addFriendRequestListener(friendRequestListener)
        WebSocketManager.addFriendRequestResultListener(friendRequestResultListener)
        // 每次恢复时都重新加载一次
        loadPendingRequests()
        loadContacts()  // 也重新加载联系人列表
    }

    override fun onPause() {
        super.onPause()
        WebSocketManager.removeFriendRequestListener(friendRequestListener)
        WebSocketManager.removeFriendRequestResultListener(friendRequestResultListener)
    }

    // 修改好友请求监听器，使用累加方式更新计数
    private val friendRequestListener = { request: FriendRequest ->
        if (isAdded && request.sender != null) {
            requireActivity().runOnUiThread {
                pendingRequestCount++
                updateFriendRequestBadge()
            }
        }
    }

    // 修改好友请求结果监听器
    private val friendRequestResultListener: (Long, Boolean) -> Unit = { requestId, accepted ->
        if (isAdded) {
            requireActivity().runOnUiThread {
                pendingRequestCount = maxOf(0, pendingRequestCount - 1)
                updateFriendRequestBadge()
                
                if (accepted) {
                    loadContacts()  // 如果接受了好友请求，重新加载联系人列表
                }
            }
        }
    }
}
class ContactsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> {
                val fragment = FriendsListFragment()
                // 不要尝试设置 tag，而是使用 position 来识别 Fragment
                fragment
            }
            1 -> GroupListFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
