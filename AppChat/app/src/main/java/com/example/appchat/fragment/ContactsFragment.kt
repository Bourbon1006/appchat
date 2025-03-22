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
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.appchat.activity.ChatActivity
import com.example.appchat.activity.MainActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Contact
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.example.appchat.databinding.FragmentContactsBinding
import com.example.appchat.websocket.WebSocketManager
import com.example.appchat.dialog.FriendRequestsDialog
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.appchat.viewmodel.ContactsViewModel
import android.util.Log
import com.example.appchat.adapter.FriendRequestAdapter

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

    // 在ContactsFragment类中添加一个标记
    private var isDataLoaded = false

    // 添加时间戳
    private var lastContactsLoadTime = 0L
    private val THROTTLE_DELAY = 500L  // 500毫秒内不重复加载

    private val viewModel by viewModels<ContactsViewModel>()

    private var friendRequestsDialog: FriendRequestsDialog? = null

    private var pendingRequestsAdapter: FriendRequestAdapter? = null

    private val currentUserId: Long by lazy {
        UserPreferences.getUserId(requireContext())
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
        setupRecyclerViews()
        setupWebSocketListeners()
        loadFriendRequests()
    }
    
    private fun setupRecyclerViews() {
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

        // 设置好友请求适配器
        pendingRequestsAdapter = FriendRequestAdapter(
            onAccept = { user ->
                user.requestId?.let { requestId ->
                    handleFriendRequest(requestId, true)
                }
            },
            onReject = { user ->
                user.requestId?.let { requestId ->
                    handleFriendRequest(requestId, false)
                }
            }
        )
        
        binding.friendRequestsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pendingRequestsAdapter
        }
    }
    
    private fun loadContacts() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📝 ContactsFragment.loadContacts() for user: $userId")
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriends(userId)
                if (response.isSuccessful) {
                    val userList = response.body() ?: emptyList()
                    println("✅ ContactsFragment loaded ${userList.size} contacts")
                    
                    // 找到当前可见的FriendsListFragment并更新
                    childFragmentManager.fragments.forEach { fragment ->
                        if (fragment is FriendsListFragment) {
                            activity?.runOnUiThread {
                                println("🔄 Notifying FriendsListFragment to update")
                                fragment.refreshContacts()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error loading contacts: ${e.message}")
            }
        }
    }

    private fun setupViewPager() {
        viewPager.adapter = ContactsPagerAdapter(this)
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
            showFriendRequestsDialog()
        }
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
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriendRequests(userId)
                if (response.isSuccessful) {
                    response.body()?.let { requests ->
                        showFriendRequestsDialog(requests)
                    }
                } else {
                    Toast.makeText(context, "加载好友请求失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFriendRequestsDialog(requests: List<UserDTO>) {
        friendRequestsDialog = FriendRequestsDialog(
            context = requireContext(),
            onAccept = { user ->
                // 处理接受请求
                lifecycleScope.launch {
                    try {
                        val response = user.requestId?.let {
                            ApiClient.apiService.acceptFriendRequest(
                                requestId = it,
                                accept = true
                            )
                        }
                        if (response != null) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "已接受好友请求", Toast.LENGTH_SHORT).show()
                                // 重新加载好友请求列表
                                loadFriendRequests()
                                // 重新加载联系人列表
                                loadContacts()
                                // 更新对话框中的请求列表
                                val updatedRequests = requests.filter { it.id != user.id }
                                friendRequestsDialog?.updateRequests(updatedRequests)
                                // 如果没有更多请求，关闭对话框
                                if (updatedRequests.isEmpty()) {
                                    friendRequestsDialog?.dismiss()
                                }
                            } else {
                                Toast.makeText(context, "接受好友请求失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReject = { user ->
                // 处理拒绝请求
                lifecycleScope.launch {
                    try {
                        val response = user.requestId?.let {
                            ApiClient.apiService.rejectFriendRequest(
                                requestId = it,
                                accept = false
                            )
                        }
                        if (response != null) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "已拒绝好友请求", Toast.LENGTH_SHORT).show()
                                // 重新加载好友请求列表
                                loadFriendRequests()
                                // 更新对话框中的请求列表
                                val updatedRequests = requests.filter { it.id != user.id }
                                friendRequestsDialog?.updateRequests(updatedRequests)
                                // 如果没有更多请求，关闭对话框
                                if (updatedRequests.isEmpty()) {
                                    friendRequestsDialog?.dismiss()
                                }
                            } else {
                                Toast.makeText(context, "拒绝好友请求失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            coroutineScope = lifecycleScope
        )
        
        friendRequestsDialog?.show()
        friendRequestsDialog?.updateRequests(requests)
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
        // 不要在这里移除所有监听器，可能会影响MainActivity的监听
        // WebSocketManager.removeAllListeners()
        
        // 只移除本Fragment添加的监听器
        // WebSocketManager.removePendingRequestCountListener(pendingRequestCountListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 只有当Fragment真正销毁时才移除监听器
        // 移除待处理请求数量监听器
        WebSocketManager.removePendingRequestCountListener(pendingRequestCountListener)
    }

    override fun onResume() {
        super.onResume()
        // 添加其他事件监听器
        WebSocketManager.addFriendRequestListener { request ->
            if (isAdded) {
                activity?.runOnUiThread {
                    println("📬 Received friend request: ${request.sender?.username}")
                    // 刷新好友请求列表
                    loadFriendRequests()
                    // 更新角标
                    (activity as? MainActivity)?.refreshPendingRequests()
                }
            }
        }
        WebSocketManager.addFriendRequestResultListener { requestId, accepted ->
            if (isAdded) {
                activity?.runOnUiThread {
                    println("📝 Friend request ${requestId} ${if (accepted) "accepted" else "rejected"}")
                    // 刷新好友请求列表
                    loadFriendRequests()
                    // 更新角标
                    (activity as? MainActivity)?.refreshPendingRequests()
                    // 如果接受了请求，刷新联系人列表
                    if (accepted) {
                        val currentFragment = (binding.viewPager.adapter as? ContactsPagerAdapter)
                            ?.getFragmentAt(binding.viewPager.currentItem)
                        if (currentFragment is FriendsListFragment) {
                            currentFragment.refreshContacts()
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 不要在onPause中移除监听器，因为切换标签页会调用onPause
        // WebSocketManager.removeAllListeners()
        
        // 当用户离开页面较长时间后，需要重新加载数据
        if (isDetached) {
            isDataLoaded = false
        }
    }

    private fun setupWebSocketListeners() {
        // 监听好友请求
        WebSocketManager.addFriendRequestListener { request ->
            if (isAdded) {
                activity?.runOnUiThread {
                    println("📬 Received friend request: ${request.sender?.username}")
                    // 刷新好友请求列表
                    loadFriendRequests()
                    // 更新角标
                    (activity as? MainActivity)?.refreshPendingRequests()
                }
            }
        }

        // 监听好友请求处理结果
        WebSocketManager.addFriendRequestResultListener { requestId, accepted ->
            if (isAdded) {
                activity?.runOnUiThread {
                    println("📝 Friend request ${requestId} ${if (accepted) "accepted" else "rejected"}")
                    // 刷新好友请求列表
                    loadFriendRequests()
                    // 更新角标
                    (activity as? MainActivity)?.refreshPendingRequests()
                    // 如果接受了请求，刷新联系人列表
                    if (accepted) {
                        val currentFragment = (binding.viewPager.adapter as? ContactsPagerAdapter)
                            ?.getFragmentAt(binding.viewPager.currentItem)
                        if (currentFragment is FriendsListFragment) {
                            currentFragment.refreshContacts()
                        }
                    }
                }
            }
        }
    }
    
    fun loadFriendRequests() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📝 Loading friend requests for user: $userId")
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPendingRequests(userId)
                if (response.isSuccessful) {
                    val requests = response.body() ?: emptyList()
                    println("✅ Loaded ${requests.size} friend requests")
                    
                    // 更新UI
                    activity?.runOnUiThread {
                        // 将 FriendRequest 转换为 UserDTO
                        val userDTOs = requests.map { request ->
                            UserDTO(
                                id = request.sender.id,
                                username = request.sender.username,
                                nickname = request.sender.nickname,
                                avatarUrl = request.sender.avatarUrl,
                                onlineStatus = request.sender.onlineStatus,
                                requestId = request.id  // 保存请求ID，用于后续处理
                            )
                        }
                        
                        pendingRequestsAdapter?.submitList(userDTOs)
                        
                        // 更新UI显示
                        updatePendingRequestCountUI(requests.size)
                        
                        // 确保主Activity角标也更新
                        (activity as? MainActivity)?.refreshPendingRequests()
                    }
                } else {
                    println("❌ Failed to load friend requests: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Error loading friend requests: ${e.message}")
                Log.e("ContactsFragment", "Error loading friend requests", e)
            }
        }
    }
    
    private fun handleFriendRequest(requestId: Long, accept: Boolean) {
        lifecycleScope.launch {
            try {
                println("📝 Handling friend request: $requestId, accept=$accept")
                val response = ApiClient.apiService.handleFriendRequest(requestId, accept)
                if (response.isSuccessful) {
                    println("✅ Friend request handled successfully")
                    
                    // 立即更新MainActivity的角标和ContactsFragment的UI
                    activity?.runOnUiThread {
                        (activity as? MainActivity)?.refreshPendingRequests()
                        
                        // 显示操作结果
                        val message = if (accept) "已接受好友请求" else "已拒绝好友请求"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    
                    if (accept) {
                        // 使用更可靠的方式获取FriendsListFragment
                        val friendsListFragment = FriendsListFragment()
                        
                        // 直接在ViewPager的当前页面刷新
                        if (binding.viewPager.currentItem == 0) {
                            // 直接调用loadContacts()方法而不是尝试获取Fragment
                            loadContacts()
                            println("✅ Directly refreshing contacts list in ContactsFragment")
                        }
                        
                        // 同时尝试通过adapter刷新Fragment
                        val adapter = binding.viewPager.adapter as? ContactsPagerAdapter
                        adapter?.getFragmentAt(0)?.let { fragment ->
                            if (fragment is FriendsListFragment) {
                                println("✅ Also refreshing FriendsListFragment via adapter")
                                fragment.refreshContacts()
                            }
                        }
                    }
                    
                    // 直接加载更新的请求列表
                    loadFriendRequests()
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "操作失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                println("❌ Error handling friend request: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    // 添加这个新方法来更新UI
    fun updatePendingRequestCountUI(count: Int) {
        if (isAdded && view != null) {
            pendingRequestCount = count
            binding.friendRequestBadge.apply {
                visibility = if (count > 0) View.VISIBLE else View.GONE
                text = count.toString()
            }
            binding.friendRequestsLayout.visibility = 
                if (count > 0) View.VISIBLE else View.GONE
        }
    }
}

class ContactsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FriendsListFragment().also { fragments[position] = it }
            1 -> GroupListFragment().also { fragments[position] = it }
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }

    fun getFragmentAt(position: Int): Fragment? {
        return fragments[position]
    }
}
