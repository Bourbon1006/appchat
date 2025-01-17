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
    }

    private fun setupViews() {
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messageList = findViewById(R.id.messageList)

        messageAdapter = MessageAdapter(UserPreferences.getUserId(this))
        messageList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
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
                                    messageAdapter.addMessage(message)
                                    messageList.scrollToPosition(messageAdapter.itemCount - 1)
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
                        println("Error parsing message: $text")
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
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_contacts, null)
        val contactsList = view.findViewById<RecyclerView>(R.id.contactsList)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchButton = view.findViewById<Button>(R.id.searchButton)

        // 设置联系人适配器
        var adapter = ContactAdapter { user ->
            currentChatUserId = user.id
            title = "与 ${user.username} 聊天中"
            messageAdapter.clearMessages()
            dialog.dismiss()
        }
        contactAdapter = adapter

        // 设置搜索结果适配器
        val searchAdapter = SearchUserAdapter(UserPreferences.getUserId(this)) { user ->
            webSocket.sendDebug(mapOf(
                "type" to "FRIEND_REQUEST",
                "senderId" to UserPreferences.getUserId(this),
                "receiverId" to user.id
            ))
            Toast.makeText(this, "已发送好友请求", Toast.LENGTH_SHORT).show()
        }

        contactsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }

        // 加载联系人列表
        loadContacts(adapter, progressBar)

        // 设置搜索功能
        fun performSearch(keyword: String) {
            if (keyword.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                contactsList.adapter = searchAdapter
                
                ApiClient.service.searchUsers(keyword)
                    .enqueue(object : Callback<List<User>> {
                        override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                            progressBar.visibility = View.GONE
                            if (response.isSuccessful) {
                                response.body()?.let { users ->
                                    searchAdapter.updateUsers(users)
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "搜索失败", Toast.LENGTH_SHORT).show()
                                contactsList.adapter = adapter
                            }
                        }

                        override fun onFailure(call: Call<List<User>>, t: Throwable) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                            contactsList.adapter = adapter
                        }
                    })
            } else {
                contactsList.adapter = adapter
            }
        }

        // 搜索按钮点击事件
        searchButton.setOnClickListener {
            performSearch(searchInput.text.toString())
        }

        // 搜索框回车事件
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

    private fun loadContacts(adapter: ContactAdapter, progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
        ApiClient.service.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            adapter.updateContacts(contacts)
                            if (contacts.isEmpty()) {
                                Toast.makeText(this@MainActivity, "暂无联系人", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "获取联系人列表失败", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showSearchDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索用户")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_search_user, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchResults = view.findViewById<RecyclerView>(R.id.searchResults)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        val adapter = SearchUserAdapter(UserPreferences.getUserId(this)) { user ->
            // 发送好友请求
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

        searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = searchInput.text.toString()
                if (keyword.isNotEmpty()) {
                    progressBar.visibility = View.VISIBLE
                    ApiClient.service.searchUsers(keyword)
                        .enqueue(object : Callback<List<User>> {
                            override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                                progressBar.visibility = View.GONE
                                if (response.isSuccessful) {
                                    response.body()?.let { users ->
                                        adapter.updateUsers(users)
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "搜索失败", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<List<User>>, t: Throwable) {
                                progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
                            }
                        })
                }
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
        ApiClient.service.getUserContacts(UserPreferences.getUserId(this))
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

            ApiClient.service.createGroup(request)
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
            .setTitle("群聊列表")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_group_list, null)
        val groupList = view.findViewById<RecyclerView>(R.id.groupList)
        val createGroupButton = view.findViewById<Button>(R.id.createGroupButton)

        val groupAdapter = GroupAdapter { selectedGroup: Group ->
            currentChatGroupId = selectedGroup.id
            title = selectedGroup.name
            loadGroupMessages(selectedGroup.id)
            dialog.dismiss()
        }

        groupList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = groupAdapter
        }

        createGroupButton.setOnClickListener {
            showCreateGroupDialog()
        }

        // 加载群组列表
        ApiClient.service.getUserGroups(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<Group>> {
                override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { groups ->
                            groupAdapter.updateGroups(groups)
                        }
                    }
                }

                override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载群组失败", Toast.LENGTH_SHORT).show()
                }
            })

        dialog.setView(view)
        dialog.show()
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
                ApiClient.service.removeGroupMember(groupId, user.id)
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
        ApiClient.service.getUserContacts(UserPreferences.getUserId(this))
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            val availableContacts = contacts.filter { contact ->
                                !group.members.any { it.id == contact.id }
                            }
                            
                            val adapter = ContactSelectionAdapter(availableContacts) { selectedUser: User ->
                                ApiClient.service.addGroupMember(group.id, selectedUser.id)
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

    private fun loadGroupMessages(groupId: Long) {
        ApiClient.service.getGroupMessages(groupId)
            .enqueue(object : Callback<List<ChatMessage>> {
                override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { messages ->
                            messageAdapter.clearMessages()
                            messages.forEach { message ->
                                messageAdapter.addMessage(message)
                            }
                            messageList.scrollToPosition(messageAdapter.itemCount - 1)
                        }
                    }
                }

                override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "加载消息失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun handleCreateGroup(name: String, memberIds: List<Long>) {
        val request = CreateGroupRequest(
            name = name,
            creatorId = UserPreferences.getUserId(this),
            memberIds = memberIds
        )

        ApiClient.service.createGroup(request).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "群组创建成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "创建群组失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadGroups() {
        val userId = UserPreferences.getUserId(this)
        ApiClient.service.getUserGroups(userId).enqueue(object : Callback<List<Group>> {
            override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                if (response.isSuccessful) {
                    response.body()?.let { groups ->
                        // 更新群组列表
                        showGroupListDialog()
                    }
                }
            }

            override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "加载群组失败", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Activity destroyed")
    }
}