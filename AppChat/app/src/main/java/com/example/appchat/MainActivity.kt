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
import com.example.appchat.websocket.WebSocketManager

class MainActivity : AppCompatActivity() {
    private lateinit var webSocket: WebSocket
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var toolbar: Toolbar
    private var userId: Long = -1L  // 添加userId作为类成员变量
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(MessageType::class.java, object : TypeAdapter<MessageType>() {
            override fun write(out: JsonWriter, value: MessageType?) {
                out.value(value?.name)
            }

            override fun read(input: JsonReader): MessageType {
                return MessageType.valueOf(input.nextString())
            }
        })
        .create()
    private var currentChatUserId: Long? = null
    private var currentUserAdapter: UserAdapter? = null
    private var contactAdapter: ContactAdapter? = null
    private var currentChatGroupId: Long? = null
    private val FILE_PICK_REQUEST = 1
    private val STORAGE_PERMISSION_REQUEST = 2
    private val apiService = ApiClient.apiService
    private var isMultiSelectMode = false
    private val selectedMessages = mutableSetOf<Long>()
    private lateinit var deleteButton: ImageButton
    private var deleteCallback: (() -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            println("All permissions granted")
            showFileChooser()
        } else {
            println("Some permissions denied")
            Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAvatar(it) }
    }

    private val avatarRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.appchat.REFRESH_AVATAR") {
                // 刷新头像
                val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
                val userId = UserPreferences.getUserId(this@MainActivity)
                val avatarUrl = "${getString(R.string.server_url_format).format(
                    getString(R.string.server_ip),
                    getString(R.string.server_port)
                )}/api/users/$userId/avatar?t=${System.currentTimeMillis()}"

                Glide.with(this@MainActivity)
                    .load(avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .into(toolbarAvatar)
            }
        }
    }

    private fun WebSocket.sendDebug(message: Any) {
        val json = gson.toJson(message)
        println("Sending: $json")
        this.send(json)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化userId
        userId = UserPreferences.getUserId(this)

        // 初始化工具栏相关视图
        deleteButton = findViewById(R.id.deleteButton)
        deleteButton.visibility = View.GONE  // 默认隐藏

        // 设置 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "聊天"
        }

        // 加载头像
        val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
        val avatarUrl = "${getString(R.string.server_url_format).format(
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )}/api/users/$userId/avatar?t=${System.currentTimeMillis()}"
        
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

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_group -> {
                    supportActionBar?.title = "群聊"
                    showGroupListDialog()
                    true
                }
                R.id.nav_contacts -> {
                    supportActionBar?.title = "联系人"
                    val fragment = ContactsFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    showProfileDialog()
                    true
                }
                else -> false
            }
        }
        
        setupViews()
        
        // 初始化WebSocket连接
        val serverUrl = "ws://${getString(R.string.server_ip)}:${getString(R.string.server_port)}/chat"
        WebSocketManager.init(serverUrl, userId)

        messageAdapter = MessageAdapter(
            context = this,
            currentUserId = userId,
            currentChatType = "group",  // 默认为群聊
            chatPartnerId = -1L,  // 默认值，表示没有特定聊天对象
            onMessageDelete = { messageId ->
                lifecycleScope.launch {
                    try {
                        println("🗑️ Starting message deletion process: $messageId")
                        
                        // 先从本地删除
                        messageAdapter.removeMessage(messageId)
                        println("✅ Local message deletion completed")
                        
                        // 然后从服务器删除
                        val response = apiService.deleteMessage(messageId, userId)
                        if (response.isSuccessful) {
                            println("✅ Server message deletion successful")
                            Toast.makeText(this@MainActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                        } else {
                            println("⚠️ Server deletion failed but local deletion succeeded: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("❌ Error in deletion process: ${e.message}")
                    }
                }
            }
        )

        messageList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true  // 从底部开始堆叠
                reverseLayout = false  // 不要反转布局
            }
            adapter = messageAdapter
            
            adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    val layoutManager = layoutManager as LinearLayoutManager
                    val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    if (lastVisiblePosition == -1 || positionStart >= messageAdapter.itemCount - 1 && lastVisiblePosition == positionStart - 1) {
                        scrollToPosition(messageAdapter.itemCount - 1)
                    }
                }
            })
        }

        // 注册广播接收器
        val filter = IntentFilter("com.example.appchat.REFRESH_AVATAR")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(avatarRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(avatarRefreshReceiver, filter)
        }
    }

    private fun setupViews() {
        messageList = findViewById(R.id.messageList)
    }

    private fun initWebSocket() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val userId = UserPreferences.getUserId(this)
        val wsUrl = getString(
            R.string.server_ws_url_format,
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )
        val request = Request.Builder()
            .url("$wsUrl?userId=$userId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    try {
                        println("⭐ Received WebSocket message: $text")
                        val wsMessage = gson.fromJson(text, WebSocketMessage::class.java)
                        println("📝 Parsed message type: ${wsMessage.type}")
                        when (wsMessage.type) {
                            "history" -> {
                                println("📜 Processing history messages")
                                wsMessage.messages?.forEach { message ->
                                    messageAdapter.addMessage(message)
                                }
                                messageList.scrollToPosition(messageAdapter.itemCount - 1)
                            }
                            "message" -> {
                                wsMessage.message?.let { message ->
                                    println("💬 Processing new message: $message")
                                    // 检查消息是否属于当前聊天
                                    val shouldAdd = when {
                                        currentChatUserId != null -> {
                                            message.senderId == currentChatUserId || message.receiverId == currentChatUserId
                                        }
                                        currentChatGroupId != null -> {
                                            message.groupId == currentChatGroupId
                                        }
                                        else -> false
                                    }
                                    println("✅ Should add message: $shouldAdd (currentChatUserId=$currentChatUserId, currentChatGroupId=$currentChatGroupId)")
                                    
                                    if (shouldAdd) {
                                        println("✅ Adding new message to local database: ${message.id}")
                                        messageAdapter.addMessage(message)
                                        messageList.scrollToPosition(messageAdapter.itemCount - 1)
                                    } else {
                                        println("⚠️ Message not for current chat, skipping")
                                    }
                                }
                            }
                            "users" -> {
                                wsMessage.users?.let { users ->
                                    println("👥 Processing users message: ${users.map { "${it.username}(${it.isOnline})" }}")
                                    updateUserList(users)
                                }
                            }
                            "userStatus" -> {
                                wsMessage.user?.let { user ->
                                    println("👤 Processing user status message: ${user.username}(online=${user.isOnline})")
                                    updateUserStatus(user)
                                }
                            }
                            "error" -> {
                                wsMessage.error?.let { error ->
                                    println("❌ Received error message: $error")
                                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                }
                            }
                            /*"pendingFriendRequests" -> {
                                println("🤝 Processing pending friend requests message")
                                wsMessage.requests?.let { requests ->
                                    println("📬 Found ${requests.size} pending requests")
                                    requests.forEach { request: FriendRequest ->
                                        println("📨 Processing request from ${request.sender.username} to ${request.receiver.username}")
                                        Toast.makeText(
                                            this@MainActivity,
                                            "收到来自 ${request.sender.username} 的好友请求",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        showFriendRequestDialog(request)
                                    }
                                }
                            }*/
                            "friendRequest" -> {
                                wsMessage.friendRequest?.let { request ->
                                    println("🤝 Received new friend request from ${request.sender.username}")
                                    Toast.makeText(
                                        this@MainActivity,
                                        "收到来自 ${request.sender.username} 的好友请求",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showFriendRequestDialog(request)
                                }
                            }
                            "friendRequestSent" -> {
                                println("✈️ Friend request sent successfully")
                                Toast.makeText(this@MainActivity, "好友请求已发送", Toast.LENGTH_SHORT).show()
                            }
                            "friendRequestResult" -> {
                                wsMessage.friendRequest?.let { request ->
                                    println("📫 Received friend request result: ${request.status} from ${request.receiver.username}")
                                    val message = when (request.status) {
                                        "ACCEPTED" -> "${request.receiver.username} 接受了你的好友请求"
                                        "REJECTED" -> "${request.receiver.username} 拒绝了你的好友请求"
                                        else -> "好友请求状态更新"
                                    }
                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                            "groupCreated" -> {
                                wsMessage.group?.let { group ->
                                    println("👥 New group created: ${group.name}")
                                    Toast.makeText(this@MainActivity, "群组 ${group.name} 创建成功", Toast.LENGTH_SHORT).show()
                                    // 立即显示群组列表对话框
                                    showGroupListDialog()
                                }
                            }
                            "groupMessage" -> {
                                wsMessage.message?.let { message ->
                                    println("👥 Received group message for group ${message.groupId}")
                                    if (message.groupId == currentChatGroupId) {
                                        println("✅ Adding group message to current chat")
                                        messageAdapter.addMessage(message)
                                        messageList.scrollToPosition(messageAdapter.itemCount - 1)
                                    } else {
                                        println("⚠️ Group message not for current chat, skipping")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("❌ Error processing WebSocket message: ${e.message}")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "WebSocket错误: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateUserList(users: List<UserDTO>) {
        println("Received users update: ${users.map { "${it.username}(online=${it.isOnline})" }}")
        currentUserAdapter?.updateUsers(users)
    }

    private fun updateUserStatus(user: UserDTO) {
        println("Processing user status message: ${user.username}(online=${user.isOnline})")
        currentUserAdapter?.updateUserStatus(user)
    }

    private fun showContactsDialog() {
        updateToolbarTitle("联系人")
        val dialog = AlertDialog.Builder(this)
            .setTitle("联系人")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_contacts, null)
        val contactsList = view.findViewById<RecyclerView>(R.id.contactsList)
        contactsList.layoutManager = LinearLayoutManager(this)

        val adapter = ContactAdapter { contact ->
            dialog.dismiss()
            startPrivateChat(contact.id)
        }
        contactsList.adapter = adapter

        // 加载联系人列表
        apiService.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<UserDTO>> {
                override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { users ->
                            adapter.updateContacts(users)
                        }
                    }
                }

                override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            })

        dialog.setView(view)
        dialog.show()
    }

    private fun showSearchDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索用户")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_user, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchResults = view.findViewById<RecyclerView>(R.id.searchResults)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val searchButton = view.findViewById<Button>(R.id.searchButton)

        val adapter = SearchUserAdapter(emptyList()) { user ->
            println("User selected: ${user.username}")
            webSocket.sendDebug(mapOf(
                "type" to "FRIEND_REQUEST",
                "senderId" to UserPreferences.getUserId(this),
                "receiverId" to user.id
            ))
            Toast.makeText(this, "已发送好友请求", Toast.LENGTH_SHORT).show()
        }

        searchResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        val performSearch = { keyword: String ->
            if (keyword.isNotEmpty()) {
                println("Performing search with keyword: $keyword")
                progressBar.visibility = View.VISIBLE
                adapter.updateContacts(emptyList()) // 清空之前的结果
                apiService.searchUsers(keyword)
                    .enqueue(object : Callback<List<UserDTO>> {
                        override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                            progressBar.visibility = View.GONE
                            if (response.isSuccessful) {
                                response.body()?.let { users ->
                                    adapter.updateContacts(users)
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "搜索失败", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener {
            performSearch(searchInput.text.toString())
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun logout() {
        UserPreferences.clear(this)
        webSocket.close(1000, "User logged out")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showFriendRequestDialog(request: FriendRequest) {
        println("Showing friend request dialog for: ${request.sender.username}")
        AlertDialog.Builder(this)
            .setTitle("好友请求")
            .setMessage("${request.sender.username} 想添加你为好友")
            .setPositiveButton("接受") { _, _ ->
                webSocket.sendDebug(mapOf(
                    "type" to "HANDLE_FRIEND_REQUEST",
                    "requestId" to request.id,
                    "accept" to true
                ))
            }
            .setNegativeButton("拒绝") { _, _ ->
                webSocket.sendDebug(mapOf(
                    "type" to "HANDLE_FRIEND_REQUEST",
                    "requestId" to request.id,
                    "accept" to false
                ))
            }
            .show()
    }

    private fun showCreateGroupDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("创建群聊")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val groupNameInput = view.findViewById<EditText>(R.id.groupNameInput)
        val contactsList = view.findViewById<RecyclerView>(R.id.contactsList)
        val createButton = view.findViewById<Button>(R.id.createButton)

        val adapter = ContactSelectionAdapter(
            contacts = emptyList(),
            onContactClick = { selectedUser: UserDTO -> 
                // 处理用户选择
            }
        )

        contactsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        // 加载联系人列表
        ApiClient.apiService.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<UserDTO>> {
                override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            adapter.updateContacts(contacts)
                        }
                    }
                }

                override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            })

        createButton.setOnClickListener {
            val groupName = groupNameInput.text.toString()
            if (groupName.isBlank()) {
                Toast.makeText(this, "请输入群组名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedContacts = adapter.getSelectedContacts()
            if (selectedContacts.isEmpty()) {
                Toast.makeText(this, "请选择群组成员", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = CreateGroupRequest(
                name = groupName,
                creatorId = UserPreferences.getUserId(this),
                memberIds = selectedContacts.map { it.id }
            )

            ApiClient.apiService.createGroup(request)
                .enqueue(object : Callback<Group> {
                    override fun onResponse(call: Call<Group>, response: Response<Group>) {
                        if (response.isSuccessful) {
                            response.body()?.let { group ->
                                updateToolbarTitle(group.name)
                                Toast.makeText(this@MainActivity, "群组创建成功", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@MainActivity, "群组创建失败", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<Group>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun showGroupListDialog() {
        updateToolbarTitle("群聊")
        val dialog = AlertDialog.Builder(this)
            .setTitle("群组")
            .setView(R.layout.dialog_group_list)
            .create()

        dialog.show()

        val groupsList = dialog.findViewById<RecyclerView>(R.id.groupsList)
        val createGroupButton = dialog.findViewById<Button>(R.id.createGroupButton)

        createGroupButton?.setOnClickListener {
            showCreateGroupDialog()
            dialog.dismiss()
        }

        val adapter = GroupAdapter { group ->
            currentChatGroupId = group.id
            currentChatUserId = null
            title = group.name
            messageAdapter = MessageAdapter(
                context = this,
                currentUserId = UserPreferences.getUserId(this),
                currentChatType = "group",
                chatPartnerId = group.id,
                onMessageDelete = { messageId ->
                    lifecycleScope.launch {
                        try {
                            println("🗑️ Starting message deletion process: $messageId")
                            
                            // 先从本地删除
                            messageAdapter.removeMessage(messageId)
                            println("✅ Local message deletion completed")
                            
                            // 然后从服务器删除
                            val response = apiService.deleteMessage(messageId, UserPreferences.getUserId(this@MainActivity))
                            if (response.isSuccessful) {
                                println("✅ Server message deletion successful")
                                Toast.makeText(this@MainActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                            } else {
                                println("⚠️ Server deletion failed but local deletion succeeded: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            println("❌ Error in deletion process: ${e.message}")
                        }
                    }
                }
            )
            messageList.adapter = messageAdapter
            loadMessages(groupId = group.id)
            dialog.dismiss()
        }

        groupsList?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        // 加载群组列表
        val userId = UserPreferences.getUserId(this)
        ApiClient.apiService.getUserGroups(userId).enqueue(object : Callback<List<Group>> {
            override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                if (response.isSuccessful) {
                    response.body()?.let { groups ->
                        adapter.updateGroups(groups)
                    }
                }
            }

            override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "加载群组失败", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendMessage(content: String) {
        val message = mutableMapOf(
            "type" to "CHAT",
            "senderId" to UserPreferences.getUserId(this),
            "senderName" to UserPreferences.getUsername(this),
            "content" to content,
            "messageType" to "TEXT"
        )

        // 根据是私聊还是群聊添加不同的字段
        currentChatGroupId?.let { groupId ->
            message["groupId"] = groupId
        }
        currentChatUserId?.let { userId ->
            message["receiverId"] = userId
        }

        webSocket.sendDebug(message)
        messageInput.text.clear()
    }

    private fun showGroupSettingsDialog(group: Group) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("群组设置")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_group_settings, null)
        val memberList = view.findViewById<RecyclerView>(R.id.memberList)
        val addMemberButton = view.findViewById<Button>(R.id.addMemberButton)

        val adapter = GroupMemberAdapter(
            members = group.members.map { member -> 
                UserDTO(
                    id = member.id,
                    username = member.username,
                    nickname = member.nickname,
                    avatarUrl = member.avatarUrl,
                    isOnline = member.isOnline
                )
            },
            currentUserId = UserPreferences.getUserId(this),
            isCreator = group.creator.id == UserPreferences.getUserId(this)
        ) { user ->
            if (group.creator.id == UserPreferences.getUserId(this)) {
                showRemoveMemberConfirmDialog(group.id, user)
            }
        }

        memberList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        addMemberButton.setOnClickListener {
            showAddMemberDialog(group)
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun showRemoveMemberConfirmDialog(groupId: Long, user: UserDTO) {
        AlertDialog.Builder(this)
            .setTitle("移除成员")
            .setMessage("确定要将 ${user.username} 移出群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                apiService.removeGroupMember(groupId, user.id)
                    .enqueue(object : Callback<Group> {
                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                            if (response.isSuccessful) {
                                Toast.makeText(this@MainActivity, "已移除成员", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "移除成员失败", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<Group>, t: Throwable) {
                            Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddMemberDialog(group: Group) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("添加群成员")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_add_member, null)
        val contactsList = view.findViewById<RecyclerView>(R.id.contactsList)

        // 过滤掉已经在群里的联系人
        apiService.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<UserDTO>> {
                override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            val availableContacts = contacts.filter { contact ->
                                !group.members.any { it.id == contact.id }
                            }
                            
                            val adapter = ContactSelectionAdapter(availableContacts) { selectedUser ->
                                apiService.addGroupMember(group.id, selectedUser.id)
                                    .enqueue(object : Callback<Group> {
                                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                                            if (response.isSuccessful) {
                                                Toast.makeText(this@MainActivity, "已添加成员", Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                            } else {
                                                Toast.makeText(this@MainActivity, "添加成员失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        override fun onFailure(call: Call<Group>, t: Throwable) {
                                            Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }

                            contactsList.apply {
                                layoutManager = LinearLayoutManager(this@MainActivity)
                                this.adapter = adapter
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            })

        dialog.setView(view)
        dialog.show()
    }

    private fun loadMessages(userId: Long? = null, groupId: Long? = null) {
        // 先从本地数据库加载消息
        messageAdapter = MessageAdapter(
            context = this,
            currentUserId = UserPreferences.getUserId(this),
            currentChatType = if (userId != null) "private" else "group",
            chatPartnerId = userId ?: groupId ?: -1L,
            onMessageDelete = { messageId ->
                lifecycleScope.launch {
                    try {
                        val response = apiService.deleteMessage(messageId, UserPreferences.getUserId(this@MainActivity))
                        if (response.isSuccessful) {
                            messageAdapter.removeMessage(messageId)
                            Toast.makeText(this@MainActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )

        messageList.adapter = messageAdapter
        val localMessages = messageAdapter.loadLocalMessages()
        println("✅ Loaded ${localMessages.size} messages from local database")

        // 然后从服务器获取最新消息
        val currentUserId = UserPreferences.getUserId(this)
        
        when {
            userId != null -> {
                // 加载私聊消息
                println("Loading private messages between $currentUserId and $userId")
                apiService.getPrivateMessages(currentUserId, userId)
                    .enqueue(object : Callback<List<ChatMessage>> {
                        override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                            if (response.isSuccessful) {
                                response.body()?.let { messages ->
                                    println("✅ Received ${messages.size} messages from server")
                                    messageAdapter.updateMessages(messages)
                                }
                            }
                        }

                        override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                            println("❌ Network error: ${t.message}")
                        }
                    })
            }
            groupId != null -> {
                // 加载群聊消息
                apiService.getGroupMessages(groupId)
                    .enqueue(object : Callback<List<ChatMessage>> {
                        override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                            if (response.isSuccessful) {
                                response.body()?.let { messages ->
                                    println("✅ Received ${messages.size} messages from server")
                                    messageAdapter.updateMessages(messages)
                                }
                            }
                        }

                        override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                            println("❌ Network error: ${t.message}")
                        }
                    })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(avatarRefreshReceiver)
        webSocket.close(1000, "Activity destroyed")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // 在多选模式下显示删除按钮，隐藏其他按钮
        menu.findItem(R.id.action_search)?.isVisible = !isMultiSelectMode
        menu.findItem(R.id.action_more)?.isVisible = !isMultiSelectMode
        menu.findItem(R.id.action_clear_chat)?.apply {
            isVisible = isMultiSelectMode
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)  // 在工具栏上直接显示
            setIcon(R.drawable.ic_delete)  // 设置删除图标
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 搜索相关
            R.id.action_search_messages -> {
                showSearchMessagesDialog()
                true
            }
            R.id.action_search_users -> {
                showSearchUsersDialog()
                true
            }

            // 更多菜单项
            R.id.menu_profile -> {
                showProfileDialog()
                true
            }
            R.id.menu_group_chat -> {
                showCreateGroupDialog()
                true
            }
            R.id.menu_contacts -> {
                showContactsDialog()
                true
            }
            R.id.action_clear_chat -> {
                showDeleteMessagesDialog()
                true
            }
            R.id.action_logout -> {
                showLogoutConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                logout()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun updateToolbarTitle(title: String) {
        findViewById<TextView>(R.id.toolbarTitle).text = title
    }

    private fun showFileChooser() {
        println("Showing file chooser")
        if (checkStoragePermission()) {
            println("Storage permission granted")
            filePickerLauncher.launch("*/*")
        } else {
            println("Requesting storage permission")
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上版本
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 及以下版本
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ))
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun sendFileMessage(fileDTO: FileDTO) {
        try {
            println("⭐ Preparing to send file message: ${fileDTO.filename}")
            
            val message = mutableMapOf<String, Any>(
                "type" to "CHAT",
                "senderId" to UserPreferences.getUserId(this),
                "senderName" to UserPreferences.getUsername(this),
                "content" to fileDTO.filename,
                "messageType" to "FILE",
                "fileUrl" to "${getString(R.string.server_url_format).format(
                    getString(R.string.server_ip),
                    getString(R.string.server_port)
                )}${fileDTO.url}"
            )

            // 添加接收者或群组ID
            currentChatGroupId?.let { groupId ->
                message["groupId"] = groupId.toLong()
                println("✅ Adding groupId: $groupId")
            } ?: currentChatUserId?.let { userId ->
                message["receiverId"] = userId.toLong()
                println("✅ Adding receiverId: $userId")
            } ?: run {
                println("❌ No chat target specified")
                return
            }
            
            println("✅ Sending file message: $message")
            webSocket.send(gson.toJson(message))
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to send file message: ${e.message}")
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadFile(uri: Uri) {
        println("⭐ Starting file upload")
        val contentResolver = applicationContext.contentResolver
        val filename = getFileName(contentResolver, uri)
        println("✅ File name: $filename")
        val inputStream = contentResolver.openInputStream(uri)
        val file = inputStream?.let { createTempFile(it, filename) }
        
        if (file != null) {
            try {
                val mediaType = contentResolver.getType(uri)?.toMediaTypeOrNull()
                val requestFile = file.asRequestBody(mediaType)
                val body = MultipartBody.Part.createFormData("file", filename, requestFile)
                val userId = UserPreferences.getUserId(this)
                
                println("✅ Uploading file: $filename")
                ApiClient.apiService.uploadFile(body).enqueue(object : Callback<FileDTO> {
                    override fun onResponse(call: Call<FileDTO>, response: Response<FileDTO>) {
                        if (response.isSuccessful) {
                            response.body()?.let { fileDTO ->
                                println("✅ File uploaded successfully: ${fileDTO.url}")
                                sendFileMessage(fileDTO)
                            }
                        } else {
                            println("❌ Upload failed: ${response.code()}")
                            Toast.makeText(this@MainActivity, "文件上传失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    override fun onFailure(call: Call<FileDTO>, t: Throwable) {
                        println("❌ Network error: ${t.message}")
                        Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error preparing file upload: ${e.message}")
                Toast.makeText(this, "文件处理失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        val displayName: String? = when (uri.scheme) {
            "content" -> {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex >= 0) {
                            it.getString(displayNameIndex)
                        } else null
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
        return displayName ?: "file"
    }

    private fun createTempFile(inputStream: InputStream, filename: String): File? {
        return try {
            val tempDir = cacheDir
            val tempFile = File.createTempFile(filename, null, tempDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showSearchMessagesDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索聊天记录")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_messages, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val resultsList = view.findViewById<RecyclerView>(R.id.searchResults)
        resultsList.layoutManager = LinearLayoutManager(this)

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString()
                searchMessages(query, resultsList) { position ->
                    dialog.dismiss()
                    messageList.scrollToPosition(position)
                    messageAdapter.highlightMessage(position)
                }
                true
            } else {
                false
            }
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun searchMessages(query: String, resultsList: RecyclerView, onItemClick: (Int) -> Unit) {
        // 在当前消息列表中搜索
        val searchResults = messageAdapter.searchMessages(query)
        
        // 创建搜索结果适配器
        val adapter = SearchResultAdapter(searchResults, onItemClick)
        
        resultsList.adapter = adapter
    }

    private fun showProfileDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("个人资料")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_profile, null)
        val avatarImage = view.findViewById<ImageView>(R.id.avatarImage)
        val nicknameInput = view.findViewById<EditText>(R.id.nicknameInput)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        // 加载当前头像和昵称
        loadCurrentUserProfile(avatarImage, nicknameInput)

        // 点击头像更换
        avatarImage.setOnClickListener {
            avatarPickerLauncher.launch("image/*")
        }

        // 保存按钮
        saveButton.setOnClickListener {
            val newNickname = nicknameInput.text.toString()
            updateUserProfile(newNickname)
            dialog.dismiss()
        }

        // 在头像更新成功后，也更新右上角的头像
        val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
        val userId = UserPreferences.getUserId(this)
        val avatarUrl = "${getString(R.string.server_url_format).format(
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )}/api/users/$userId/avatar?t=${System.currentTimeMillis()}"
        
        Glide.with(this)
            .load(avatarUrl)
            .apply(RequestOptions.circleCropTransform())
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(toolbarAvatar)

        dialog.setView(view)
        dialog.show()
    }

    private fun updateUserProfile(nickname: String) {
        val userId = UserPreferences.getUserId(this)
        val request = UpdateUserRequest(nickname = nickname)
        
        apiService.updateUser(userId, request).enqueue(object : Callback<UserDTO> {
            override fun onResponse(call: Call<UserDTO>, response: Response<UserDTO>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "个人资料更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "更新失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UserDTO>, t: Throwable) {
                Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDeleteMessagesDialog() {
        val selectedMessages = messageAdapter.getSelectedMessages()
        if (selectedMessages.isEmpty()) {
            Toast.makeText(this, "请选择要删除的消息", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
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
                messageAdapter.getSelectedMessages().forEach { messageId ->
                    val response = apiService.deleteMessage(
                        messageId, 
                        UserPreferences.getUserId(this@MainActivity)
                    )
                    if (response.isSuccessful) {
                        response.body()?.let { deleteResponse ->
                            if (deleteResponse.isFullyDeleted) {
                                messageAdapter.removeMessageCompletely(messageId)
                            } else {
                                messageAdapter.removeMessage(messageId)
                            }
                        }
                    }
                }
                exitMultiSelectMode()
                Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        messageAdapter.setMultiSelectMode(true)
        updateToolbarTitle("已选择 0 条消息")
        invalidateOptionsMenu()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedMessages.clear()
        messageAdapter.setMultiSelectMode(false)
        updateToolbarTitle(getCurrentChatTitle())
        invalidateOptionsMenu()
    }

    private fun getCurrentChatTitle(): String {
        return when {
            currentChatUserId != null -> "私聊"
            currentChatGroupId != null -> "群聊"
            else -> "聊天"
        }
    }

    private fun loadCurrentUserProfile(avatarImage: ImageView, nicknameInput: EditText) {
        val userId = UserPreferences.getUserId(this)
        
        // 加载头像
        val avatarUrl = "${getString(R.string.server_url_format).format(
            getString(R.string.server_ip),
            getString(R.string.server_port)
        )}/api/users/$userId/avatar"
        
        Glide.with(this)
            .load(avatarUrl)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(avatarImage)

        // 加载用户信息
        apiService.getUser(userId).enqueue(object : Callback<UserDTO> {
            override fun onResponse(call: Call<UserDTO>, response: Response<UserDTO>) {
                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        nicknameInput.setText(user.nickname)
                    }
                }
            }

            override fun onFailure(call: Call<UserDTO>, t: Throwable) {
                Toast.makeText(this@MainActivity, "加载用户信息失败", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendFriendRequest(receiverId: Long) {
        webSocket.sendDebug(mapOf(
            "type" to "FRIEND_REQUEST",
            "senderId" to UserPreferences.getUserId(this),
            "receiverId" to receiverId
        ))
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

    fun startPrivateChat(userId: Long) {
        currentChatUserId = userId
        currentChatGroupId = null
        
        // 获取用户信息并更新标题
        apiService.getUser(userId).enqueue(object : Callback<UserDTO> {
            override fun onResponse(call: Call<UserDTO>, response: Response<UserDTO>) {
                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        updateToolbarTitle("与 ${user.nickname ?: user.username} 聊天中")
                    }
                }
            }
            override fun onFailure(call: Call<UserDTO>, t: Throwable) {
                updateToolbarTitle("私聊")
            }
        })
        
        loadMessages(userId = userId)
    }

    private fun uploadAvatar(uri: Uri) {
        val contentResolver = applicationContext.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val file = inputStream?.let { createTempFile(it, "avatar_temp") }

        if (file != null) {
            val mediaType = contentResolver.getType(uri)?.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val body = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestFile)

            val userId = UserPreferences.getUserId(this)
            apiService.uploadAvatar(userId, body).enqueue(object : Callback<UserDTO> {
                override fun onResponse(call: Call<UserDTO>, response: Response<UserDTO>) {
                    if (response.isSuccessful) {
                        // 清除缓存并强制从服务器获取新头像
                        Glide.get(this@MainActivity).clearMemory()
                        Thread {
                            Glide.get(this@MainActivity).clearDiskCache()
                        }.start()
                        
                        Handler(Looper.getMainLooper()).post {
                            val avatarUrl = "${getString(R.string.server_url_format).format(
                                getString(R.string.server_ip),
                                getString(R.string.server_port)
                            )}/api/users/$userId/avatar?t=${System.currentTimeMillis()}"
                            
                            val toolbarAvatar = findViewById<ImageView>(R.id.toolbarAvatar)
                            Glide.with(this@MainActivity)
                                .load(avatarUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .skipMemoryCache(true)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .placeholder(R.drawable.default_avatar)
                                .error(R.drawable.default_avatar)
                                .into(toolbarAvatar)
                            
                            Toast.makeText(this@MainActivity, "头像更新成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "头像更新失败", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<UserDTO>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    fun updateSelectedCount(count: Int) {
        if (isMultiSelectMode) {
            updateToolbarTitle("已选择 $count 条消息")
            if (count == 0) {
                exitMultiSelectMode()
            }
        }
    }

    // 添加返回键处理
    override fun onBackPressed() {
        if (isMultiSelectMode) {
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    fun showMultiSelectActionBar() {
        // 显示多选模式的工具栏
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "选择要删除的消息"
        }
        
        // 隐藏普通工具栏内容，显示删除按钮
        findViewById<LinearLayout>(R.id.toolbarContent).visibility = View.GONE
        deleteButton.visibility = View.VISIBLE
    }

    fun hideMultiSelectActionBar() {
        // 隐藏多选模式的工具栏
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            title = "聊天"
        }
        
        // 显示普通工具栏内容，隐藏删除按钮
        findViewById<LinearLayout>(R.id.toolbarContent).visibility = View.VISIBLE
        deleteButton.visibility = View.GONE
    }

    fun showDeleteButton(callback: () -> Unit) {
        deleteButton.visibility = View.VISIBLE
        deleteCallback = callback
        deleteButton.setOnClickListener {
            deleteCallback?.invoke()
        }
    }

    companion object {
        private const val FILE_PICK_REQUEST = 1
        private const val STORAGE_PERMISSION_REQUEST = 2
    }
}