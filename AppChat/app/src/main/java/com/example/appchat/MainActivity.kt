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
                    currentChatUserId = null
                    title = "群聊"
                    messageAdapter.clearMessages()
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
                val chatMessage = mapOf(
                    "type" to "CHAT",
                    "senderId" to UserPreferences.getUserId(this),
                    "senderName" to UserPreferences.getUsername(this),
                    "content" to message,
                    "receiverId" to currentChatUserId,
                    "messageType" to "TEXT"
                )
                webSocket.sendDebug(chatMessage)
                messageInput.text.clear()
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

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Activity destroyed")
    }
}