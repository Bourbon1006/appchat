package com.example.appchat.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.adapter.SearchUserAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.model.UserDTO
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.*
import com.example.appchat.fragment.ContactsFragment
import com.example.appchat.fragment.MessageDisplayFragment
import com.example.appchat.websocket.WebSocketManager
import androidx.fragment.app.Fragment
import org.json.JSONObject
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.example.appchat.api.RetrofitClient
import android.util.Log
import com.example.appchat.R
import com.example.appchat.databinding.ActivityMainBinding
import com.example.appchat.fragment.MomentsFragment
import kotlinx.coroutines.Dispatchers
import com.example.appchat.fragment.MeFragment
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

class MainActivity : AppCompatActivity() {
    // 将 binding 改为 internal，这样同包的类可以访问
    private var _binding: ActivityMainBinding? = null
    internal val binding: ActivityMainBinding
        get() = _binding!!

    private var currentUserId: Long = -1
    private lateinit var toolbar: Toolbar
    private lateinit var deleteButton: ImageButton
    private var deleteCallback: (() -> Unit)? = null
    private val apiService = ApiClient.apiService
    private var pendingRequestCount = 0
    private val pendingRequestCountListener: (Int) -> Unit = { count ->
        pendingRequestCount = count
        updateContactsBadge()
    }

    private var avatarRefreshReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在加载布局之前切换回正常主题
        setTheme(R.style.Theme_AppChat)
        
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取当前用户ID
        currentUserId = UserPreferences.getUserId(this)
        
        // 验证用户ID
        if (currentUserId <= 0) {
            println("❌ Invalid userId: $currentUserId")
            Toast.makeText(this, "用户信息无效，请重新登录", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        // 设置底部导航栏
        setupBottomNavigation()
        
        // 使用协程异步加载数据
        lifecycleScope.launch {
            try {
                // 在后台加载数据
                withContext(Dispatchers.IO) {
                    // 预加载必要的数据
                    preloadData()
                }
                
                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    // 初始化其他UI组件
                    setupUI()
                    // 加载默认Fragment
                    loadDefaultFragment()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "加载数据失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun preloadData() {
        coroutineScope {
            launch { 
                WebSocketManager.init(
                    context = this@MainActivity,
                    userId = currentUserId.toString()
                )
            }
            // 其他预加载操作...
        }
    }
    
    private fun setupUI() {
        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        
        // 初始化ViewPager或其他UI组件
        // ...
    }
    
    private fun loadDefaultFragment() {
        // 加载默认Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MessageDisplayFragment())
            .commit()
    }

    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "聊天"
        }

        // 加载头像
        val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
        val avatarUrl = UserPreferences.getAvatarUrl(this)
        
        Glide.with(this)
            .load(avatarUrl)
            .apply(RequestOptions.circleCropTransform())
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(toolbarAvatar)

        // 点击头像跳转到个人资料页面
        toolbarAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chat -> {
                    loadFragment(MessageDisplayFragment())
                    true
                }
                R.id.navigation_contacts -> {
                    loadFragment(ContactsFragment())
                    true
                }
                R.id.navigation_moments -> {
                    loadFragment(MomentsFragment())
                    true
                }
                R.id.navigation_me -> {
                    loadFragment(MeFragment())
                    true
                }
                else -> false
            }
        }

        // 设置默认选中项
        binding.bottomNavigation.selectedItemId = R.id.navigation_chat
    }

    private fun loadFragment(fragment: Fragment) {
        // 先检查当前显示的是否就是要切换到的 Fragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment?.javaClass == fragment.javaClass) {
            return
        }

        // 使用 replace 而不是 add，并保存到回退栈
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupAvatarRefreshReceiver() {
        avatarRefreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.appchat.AVATAR_UPDATED") {
                    // 刷新头像
                    val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
                    val avatarUrl = UserPreferences.getAvatarUrl(this@MainActivity)
                    
                    Glide.with(this@MainActivity)
                        .load(avatarUrl)
                        .apply(RequestOptions.circleCropTransform())
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .placeholder(R.drawable.default_avatar)
                        .into(toolbarAvatar)
                }
            }
        }

        // 注册广播接收器
        try {
            registerReceiver(
                avatarRefreshReceiver,
                IntentFilter("com.example.appchat.AVATAR_UPDATED"),
                RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            println("❌ Failed to register avatar refresh receiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            // 只有在接收器已注册的情况下才注销
            if (isReceiverRegistered && avatarRefreshReceiver != null) {
                unregisterReceiver(avatarRefreshReceiver)
                isReceiverRegistered = false
            }
            
            // 清理 WebSocket 监听器
            WebSocketManager.removeFriendRequestListeners()
            
        } catch (e: Exception) {
            println("❌ Error in onDestroy: ${e.message}")
        }
        
        super.onDestroy()
        _binding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSessionUpdate(@Suppress("UNUSED_PARAMETER") event: ChatActivity.SessionUpdateEvent) {
        // 收到会话更新事件，刷新会话列表
        loadMessageSessions()
    }

    private fun loadMessageSessions() {
        val userId = UserPreferences.getUserId(this)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.messageService.getMessageSessions(userId)
                if (response.isSuccessful) {
                    response.body()?.let { sessions ->
                        // 更新 MessageDisplayFragment 的会话列表
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        if (fragment is MessageDisplayFragment) {
                            // 转换 DTO 到 MessageSession
                            val messageSessions = sessions.map { it.toMessageSession() }
                            fragment.updateSessions(messageSessions)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading sessions", e)
            }
        }
    }

    // 添加菜单相关的对话框方法
    private fun showSearchMessagesDialog() {
        // TODO: 实现搜索消息对话框
    }

    private fun sendFriendRequest(receiverId: Long) {
        try {
            val userId = UserPreferences.getUserId(this)
            
            // 如果 WebSocket 未连接，先初始化
            if (!WebSocketManager.isConnected()) {
                WebSocketManager.init(
                    context = this,  // 传入 Context
                    userId = userId.toString()  // 转换为 String
                )
                // 等待一会儿确保连接建立
                Handler(Looper.getMainLooper()).postDelayed({
                    sendFriendRequestInternal(userId, receiverId)
                }, 1000)
            } else {
                sendFriendRequestInternal(userId, receiverId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "发送好友请求失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFriendRequestInternal(userId: Long, receiverId: Long) {
        val requestJson = JSONObject().apply {
            put("type", "FRIEND_REQUEST")
            put("senderId", userId)
            put("receiverId", receiverId)
        }.toString()
        
        WebSocketManager.sendFriendRequest(requestJson,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "好友请求已发送", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    Toast.makeText(this, "发送失败: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showSearchUsersDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索用户")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_users, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val resultsList = view.findViewById<RecyclerView>(R.id.searchResults)
        resultsList.layoutManager = LinearLayoutManager(this)

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString()
                apiService.searchUsers(query).enqueue(object : Callback<List<UserDTO>> {
                    override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                        if (response.isSuccessful) {
                            response.body()?.let { users ->
                                val adapter = SearchUserAdapter(users) { user ->
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("添加好友")
                                        .setMessage("确定要添加 ${user.nickname ?: user.username} 为好友吗？")
                                        .setPositiveButton("确定") { _, _ ->
                                            sendFriendRequest(user.id)
                                            Toast.makeText(this@MainActivity, "已发送好友请求", Toast.LENGTH_SHORT).show()
                                            dialog.dismiss()
                                        }
                                        .setNegativeButton("取消", null)
                                        .show()
                                }
                                resultsList.adapter = adapter
                            }
                        }
                    }

                    override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "搜索失败", Toast.LENGTH_SHORT).show()
                    }
                })
                true
            } else {
                false
            }
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun showProfileDialog() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun showCreateGroupDialog() {
        // TODO: 实现创建群聊对话框
    }

    private fun showDeleteMessagesDialog() {
        // TODO: 实现删除消息对话框
    }

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                UserPreferences.clearUserData(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 添加菜单创建方法
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // 添加菜单项点击处理
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search_messages -> {
                showSearchMessagesDialog()
                true
            }
            R.id.action_search_users -> {
                showSearchUsersDialog()
                true
            }
            R.id.menu_profile -> {
                showProfileDialog()
                true
            }
            R.id.menu_group_chat -> {
                showCreateGroupDialog()
                true
            }
            R.id.action_nearby_transfer -> {
                startActivity(Intent(this, NearbyTransferActivity::class.java))
                true
            }
            R.id.action_logout -> {
                showLogoutConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // 如果不是在聊天页面，就切换到聊天页面
        if (binding.bottomNavigation.selectedItemId != R.id.navigation_chat) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_chat
        } else {
            super.onBackPressed()
        }
    }

    private fun updateContactsBadge() {
        val badge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_contacts)
        if (pendingRequestCount > 0) {
            badge.apply {
                isVisible = true
                backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.red)
                number = pendingRequestCount
            }
        } else {
            badge.isVisible = false
        }
    }

    private fun updatePendingRequestCount() {
        println("⭐ Updating pending request count for user: $currentUserId")
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPendingRequests(currentUserId)
                if (response.isSuccessful) {
                    val requests = response.body() ?: emptyList()
                    println("✅ Found ${requests.size} pending requests")
                    
                    // 重要：不要使用runOnUiThread，因为lifecycleScope已经保证了在主线程回调
                    // 存储计数以供其他地方使用
                    pendingRequestCount = requests.size
                    
                    // 更新UI
                    val badge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_contacts)
                    if (requests.isNotEmpty()) {
                        badge.isVisible = true
                        badge.number = requests.size
                        badge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.red)
                        println("📝 Updated badge: count=${requests.size}")
                    } else {
                        badge.isVisible = false
                        println("📝 Hidden badge: no pending requests")
                    }
                    
                    // 通知所有打开的 Fragment 更新它们的UI
                    val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    if (fragment is ContactsFragment) {
                        fragment.updatePendingRequestCountUI(requests.size)
                    }
                }
            } catch (e: Exception) {
                println("❌ Error updating pending requests: ${e.message}")
            }
        }
    }

    private fun updateFriendRequestBadge(count: Int) {
        val badge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_contacts)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
        } else {
            badge.isVisible = false
        }
    }

    private fun setupWebSocket() {
        WebSocketManager.init(
            context = this,
            userId = currentUserId.toString()
        )
    }

    // 添加公共方法供 Fragment 调用
    fun refreshPendingRequests() {
        println("📝 MainActivity.refreshPendingRequests() called")
        updatePendingRequestCount()
    }

    // 修复其他调用 WebSocketManager.init 的地方
    private fun reconnectWebSocket() {
        WebSocketManager.init(
            context = this,
            userId = currentUserId.toString()
        )
    }

    private fun handleWebSocketReconnect(serverUrl: String, userId: Long) {
        WebSocketManager.init(
            context = this,
            userId = userId.toString()
        )
    }

    companion object {
        private const val FILE_PICK_REQUEST = 1
        private const val STORAGE_PERMISSION_REQUEST = 2
    }
}