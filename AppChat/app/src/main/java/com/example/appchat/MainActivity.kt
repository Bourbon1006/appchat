package com.example.appchat

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.adapter.MessageAdapter
import com.example.appchat.adapter.UserAdapter
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.adapter.SearchUserAdapter
import com.example.appchat.adapter.ContactSelectionAdapter
import com.example.appchat.adapter.GroupAdapter
import com.example.appchat.adapter.GroupMemberAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.api.LocalDateTimeAdapter
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import com.example.appchat.model.User
import com.example.appchat.util.UserPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.appchat.model.FriendRequest
import com.example.appchat.model.WebSocketMessage
import com.example.appchat.model.Group
import com.example.appchat.model.CreateGroupRequest
import android.net.Uri
import android.provider.MediaStore
import android.app.Activity
import okhttp3.MultipartBody
import okhttp3.RequestBody
import com.example.appchat.model.FileDTO
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.ContentResolver
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.appchat.adapter.SearchResultAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.model.UpdateUserRequest
import com.example.appchat.model.UserDTO
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.widget.*
import com.example.appchat.fragment.ContactsFragment
import com.example.appchat.fragment.MessageDisplayFragment
import com.example.appchat.websocket.WebSocketManager
import androidx.fragment.app.Fragment
import org.json.JSONObject
import com.example.appchat.databinding.DialogContactsBinding
import com.example.appchat.model.Contact
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.example.appchat.api.RetrofitClient
import android.util.Log
import com.example.appchat.databinding.ActivityMainBinding
import com.example.appchat.fragment.MomentsFragment
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainBinding
    private lateinit var toolbar: Toolbar
    private var userId: Long = -1L
    private lateinit var deleteButton: ImageButton
    private var deleteCallback: (() -> Unit)? = null
    private val apiService = ApiClient.apiService
    private var pendingRequestCount = 0
    private val pendingRequestCountListener: (Int) -> Unit = { count ->
        pendingRequestCount = count
        updateContactsBadge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化userId
        userId = UserPreferences.getUserId(this)

        setupToolbar()
        setupBottomNavigation()
        setupAvatarRefreshReceiver()

        // 注册 EventBus
        EventBus.getDefault().register(this)

        // 确保 WebSocket 连接
        if (!WebSocketManager.isConnected()) {
            val serverUrl = ApiClient.BASE_URL
            WebSocketManager.init(serverUrl, userId)
        }

        // 注册待处理请求数量监听器
        WebSocketManager.addPendingRequestCountListener(pendingRequestCountListener)
        
        // 注册好友请求监听器，用于实时更新角标
        WebSocketManager.addFriendRequestListener { request ->
            if (request.sender != null) {
                pendingRequestCount++
                updateContactsBadge()
            }
        }
        
        // 注册好友请求结果监听器
        WebSocketManager.addFriendRequestResultListener { _, _ ->
            pendingRequestCount = maxOf(0, pendingRequestCount - 1)
            updateContactsBadge()
        }

        // 初始加载待处理请求数量
        loadPendingRequests()
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
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
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
        val filter = IntentFilter("com.example.appchat.REFRESH_AVATAR")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(avatarRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(avatarRefreshReceiver, filter)
        }
    }

    private val avatarRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.appchat.REFRESH_AVATAR") {
                refreshAvatar()
            }
        }
    }

    private fun refreshAvatar() {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(avatarRefreshReceiver)
        EventBus.getDefault().unregister(this)
        WebSocketManager.removePendingRequestCountListener(pendingRequestCountListener)
        // 移除其他监听器
        WebSocketManager.removeFriendRequestListener { }
        WebSocketManager.removeFriendRequestResultListener { _, _ -> }
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
            val serverUrl = ApiClient.BASE_URL
            
            // 如果 WebSocket 未连接，先初始化
            if (!WebSocketManager.isConnected()) {
                WebSocketManager.init(serverUrl, userId)
                // 等待一会儿确保连接建立
                Handler(Looper.getMainLooper()).postDelayed({
                    sendFriendRequestInternal(userId, receiverId)
                }, 1000)
            } else {
                sendFriendRequestInternal(userId, receiverId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "发送好友请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

    fun loadPendingRequests() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPendingRequests(userId)
                if (response.isSuccessful) {
                    response.body()?.let { requests ->
                        pendingRequestCount = requests.size
                        updateContactsBadge()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading pending requests", e)
            }
        }
    }

    companion object {
        private const val FILE_PICK_REQUEST = 1
        private const val STORAGE_PERMISSION_REQUEST = 2
    }
}