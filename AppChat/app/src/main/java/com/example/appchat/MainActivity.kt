package com.example.appchat

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
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
import android.widget.ImageButton
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

class MainActivity : AppCompatActivity() {
    private lateinit var webSocket: WebSocket
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var toolbar: Toolbar
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

    private fun WebSocket.sendDebug(message: Any) {
        val json = gson.toJson(message)
        println("Sending: $json")
        this.send(json)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = "群聊"

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_group_chat -> {
                    showGroupListDialog()
                    true
                }
                R.id.action_contacts -> {
                    showContactsDialog()
                    true
                }
                R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
        
        setupViews()
        initWebSocket()

        messageAdapter = MessageAdapter(
            context = this,
            currentUserId = UserPreferences.getUserId(this),
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
    }

    private fun setupViews() {
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messageList = findViewById(R.id.messageList)

        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }

        findViewById<ImageButton>(R.id.attachButton).setOnClickListener {
            println("Attach button clicked")
            showFileChooser()
        }
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
                        println("Received WebSocket message: $text")
                        val wsMessage = gson.fromJson(text, WebSocketMessage::class.java)
                        when (wsMessage.type) {
                            "history" -> {
                                wsMessage.messages?.forEach { message ->
                                    messageAdapter.addMessage(message)
                                }
                                messageList.scrollToPosition(messageAdapter.itemCount - 1)
                            }
                            "message" -> {
                                wsMessage.message?.let { message ->
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
                                    println("Processing users message: ${users.map { "${it.username}(${it.isOnline})" }}")
                                    updateUserList(users)
                                }
                            }
                            "userStatus" -> {
                                wsMessage.user?.let { user ->
                                    println("Processing user status message: ${user.username}(${user.isOnline})")
                                    updateUserStatus(user)
                                }
                            }
                            "error" -> {
                                wsMessage.error?.let { error ->
                                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                }
                            }
                            "friendRequest" -> {
                                wsMessage.friendRequest?.let { request ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "收到来自 ${request.sender.username} 的好友请求",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showFriendRequestDialog(request)
                                }
                            }
                            "friendRequestSent" -> {
                                Toast.makeText(this@MainActivity, "好友请求已发送", Toast.LENGTH_SHORT).show()
                            }
                            "friendRequestResult" -> {
                                wsMessage.friendRequest?.let { request ->
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
                                    Toast.makeText(this@MainActivity, "群组 ${group.name} 创建成功", Toast.LENGTH_SHORT).show()
                                    // 立即显示群组列表对话框
                                    showGroupListDialog()
                                }
                            }
                            "groupMessage" -> {
                                wsMessage.message?.let { message ->
                                    if (message.groupId == currentChatGroupId) {
                                        messageAdapter.addMessage(message)
                                        messageList.scrollToPosition(messageAdapter.itemCount - 1)
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

    private fun updateUserList(users: List<User>) {
        println("Received users update: ${users.map { "${it.username}(online=${it.isOnline})" }}")
        currentUserAdapter?.updateUsers(users)
    }

    private fun updateUserStatus(user: User) {
        println("Received user status update: ${user.username}(online=${user.isOnline})")
        currentUserAdapter?.updateUserStatus(user)
    }

    private fun showContactsDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("联系人")
            .setView(R.layout.dialog_contacts)
            .create()

        dialog.show()

        val contactsList = dialog.findViewById<RecyclerView>(R.id.contactsList)
        val adapter = ContactAdapter { contact ->
            println("Contact clicked: ${contact.username}")
            // 设置当前聊天对象
            currentChatUserId = contact.id
            currentChatGroupId = null
            // 更新标题
            title = "与 ${contact.username} 聊天中"
            // 创建新的 MessageAdapter 实例
            messageAdapter = MessageAdapter(
                context = this,
                currentUserId = UserPreferences.getUserId(this),
                currentChatType = "private",
                chatPartnerId = contact.id,
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
            // 加载聊天记录
            loadChatHistory(contactId = contact.id)
            dialog.dismiss()
        }

        contactsList?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        // 加载联系人列表
        val userId = UserPreferences.getUserId(this)
        ApiClient.apiService.getUserContacts(userId).enqueue(object : Callback<List<User>> {
            override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                if (response.isSuccessful) {
                    response.body()?.let { contacts ->
                        adapter.updateContacts(contacts)
                    }
                }
            }

            override fun onFailure(call: Call<List<User>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showSearchDialog() {
        println("Opening search dialog")
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索用户")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_user, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchResults = view.findViewById<RecyclerView>(R.id.searchResults)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val searchButton = view.findViewById<Button>(R.id.searchButton)

        println("Setting up search adapter")
        val adapter = SearchUserAdapter(UserPreferences.getUserId(this)) { user ->
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
                adapter.updateUsers(emptyList()) // 清空之前的结果
                ApiClient.apiService.searchUsers(keyword)
                    .enqueue(object : Callback<List<User>> {
                        override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                            println("Search API response code: ${response.code()}")
                            println("Search API raw response: ${response.raw()}")
                            progressBar.visibility = View.GONE
                            if (response.isSuccessful) {
                                response.body()?.let { users ->
                                    println("Found ${users.size} users")
                                    if (users.isEmpty()) {
                                        Toast.makeText(this@MainActivity, "未找到匹配的用户", Toast.LENGTH_SHORT).show()
                                    }
                                    adapter.updateUsers(users)
                                } ?: run {
                                    println("Response body is null")
                                    Toast.makeText(this@MainActivity, "搜索结果为空", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val errorBody = response.errorBody()?.string()
                                println("Search API error: $errorBody")
                                Toast.makeText(this@MainActivity, "搜索失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<List<User>>, t: Throwable) {
                            println("Search API failure: ${t.message}")
                            t.printStackTrace()
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "网络错误: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
            } else {
                Toast.makeText(this@MainActivity, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            }
        }

        searchButton.setOnClickListener {
            performSearch(searchInput.text.toString())
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                true
            } else {
                false
            }
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
            contacts = emptyList()
        ) { _: User -> /* 这里不需要回调，使用 getSelectedContacts() 方法获取选中的联系人 */ }

        contactsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        // 加载联系人列表
        ApiClient.apiService.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            adapter.updateContacts(contacts)
                        }
                    }
                }

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
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
                            Toast.makeText(this@MainActivity, "群组创建成功", Toast.LENGTH_SHORT).show()
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
            // 设置当前聊天对象
            currentChatGroupId = group.id
            currentChatUserId = null
            // 更新标题
            title = group.name
            // 创建新的 MessageAdapter 实例
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
            // 加载聊天记录
            loadChatHistory(groupId = group.id)
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
            members = group.members,
            currentUserId = UserPreferences.getUserId(this),
            isCreator = group.creator.id == UserPreferences.getUserId(this)
        ) { user: User ->
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

    private fun showRemoveMemberConfirmDialog(groupId: Long, user: User) {
        AlertDialog.Builder(this)
            .setTitle("移除成员")
            .setMessage("确定要将 ${user.username} 移出群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                ApiClient.apiService.removeGroupMember(groupId, user.id)
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
        ApiClient.apiService.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            val availableContacts = contacts.filter { contact ->
                                !group.members.any { it.id == contact.id }
                            }
                            
                            val adapter = ContactSelectionAdapter(availableContacts) { selectedUser: User ->
                                ApiClient.apiService.addGroupMember(group.id, selectedUser.id)
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

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            })

        dialog.setView(view)
        dialog.show()
    }

    private fun loadChatHistory(contactId: Long? = null, groupId: Long? = null) {
        println("Loading chat history - contactId: $contactId, groupId: $groupId")
        
        // 先从本地加载消息
        val localMessages = messageAdapter.loadLocalMessages()
        if (localMessages.isNotEmpty()) {
            println("✅ Loaded ${localMessages.size} messages from local database")
        }

        // 然后从服务器获取最新消息
        val currentUserId = UserPreferences.getUserId(this)
        
        if (contactId != null) {
            // 加载私聊消息
            println("Loading private messages between $currentUserId and $contactId")
            ApiClient.apiService.getPrivateMessages(currentUserId, contactId)
                .enqueue(object : Callback<List<ChatMessage>> {
                    override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                        if (response.isSuccessful) {
                            response.body()?.let { messages ->
                                println("✅ Received ${messages.size} messages from server")
                                // 更新本地消息
                                messageAdapter.updateMessages(messages)
                            }
                        } else {
                            println("❌ Failed to load messages from server: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                        println("❌ Network error: ${t.message}")
                    }
                })
        } else if (groupId != null) {
            // 加载群聊消息
            ApiClient.apiService.getGroupMessages(groupId)
                .enqueue(object : Callback<List<ChatMessage>> {
                    override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                        if (response.isSuccessful) {
                            response.body()?.let { messages ->
                                println("✅ Received ${messages.size} messages from server")
                                // 更新本地消息
                                messageAdapter.updateMessages(messages)
                            }
                        } else {
                            println("❌ Failed to load messages from server: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                        println("❌ Network error: ${t.message}")
                    }
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Activity destroyed")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchDialog()
                true
            }
            R.id.action_search_messages -> {
                showSearchMessagesDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    private fun uploadFile(uri: Uri) {
        println("Starting file upload: $uri")
        val contentResolver = applicationContext.contentResolver
        val filename = getFileName(contentResolver, uri)
        println("File name: $filename")
        val inputStream = contentResolver.openInputStream(uri)
        val file = inputStream?.let { createTempFile(it, filename) }
        
        if (file != null) {
            val mediaType = contentResolver.getType(uri)?.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            
            val body = MultipartBody.Part.createFormData("file", filename, requestFile)
            
            ApiClient.apiService.uploadFile(body).enqueue(object : Callback<FileDTO> {
                override fun onResponse(call: Call<FileDTO>, response: Response<FileDTO>) {
                    if (response.isSuccessful) {
                        response.body()?.let { fileDTO ->
                            sendFileMessage(fileDTO)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "文件上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onFailure(call: Call<FileDTO>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun sendFileMessage(fileDTO: FileDTO) {
        val message = mutableMapOf(
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
        
        currentChatGroupId?.let { groupId ->
            message["groupId"] = groupId
        }
        currentChatUserId?.let { userId ->
            message["receiverId"] = userId
        }
        
        println("Sending file message: $message")
        webSocket.sendDebug(message)
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
        val searchResultsList = view.findViewById<RecyclerView>(R.id.searchResultsList)

        searchResultsList.layoutManager = LinearLayoutManager(this)
        
        // 处理搜索动作
        searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotEmpty()) {
                    searchMessages(query, searchResultsList) { position ->
                        // 点击搜索结果时滚动到对应位置
                        messageList.smoothScrollToPosition(position)
                        dialog.dismiss()
                    }
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

    companion object {
        private const val FILE_PICK_REQUEST = 1
        private const val STORAGE_PERMISSION_REQUEST = 2
    }
}