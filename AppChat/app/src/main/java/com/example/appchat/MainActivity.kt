package com.example.appchat

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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

// 首先添加一个数据类来表示 WebSocket 消息
data class WebSocketMessage(
    val type: String,
    val messages: List<ChatMessage>? = null,
    val message: ChatMessage? = null,
    val users: List<User>? = null,
    val user: User? = null,
    val error: String? = null
)

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
                R.id.action_user_list -> {
                    showUserListDialog()
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
        val request = Request.Builder()
            .url("ws://192.168.1.167:8080/chat?userId=$userId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    try {
                        println("Received WebSocket message: $text")  // 添加日志
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
                                    println("Processing users message: ${users.map { "${it.username}(${it.isOnline})" }}")  // 添加日志
                                    updateUserList(users)
                                }
                            }
                            "userStatus" -> {
                                wsMessage.user?.let { user ->
                                    println("Processing user status message: ${user.username}(${user.isOnline})")  // 添加日志
                                    updateUserStatus(user)
                                }
                            }
                            "error" -> {
                                wsMessage.error?.let { error ->
                                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Error parsing message: $text")  // 添加错误消息日志
                        Toast.makeText(this@MainActivity, "消息处理错误: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun showUserListDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择聊天对象")
            .create()

        val view = layoutInflater.inflate(R.layout.dialog_user_list, null)
        val userList = view.findViewById<RecyclerView>(R.id.userList)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        
        val userAdapter = UserAdapter(UserPreferences.getUserId(this)) { user ->
            currentChatUserId = user.id
            title = "与 ${user.username} 聊天中"
            messageAdapter.clearMessages()
            dialog.dismiss()
        }
        currentUserAdapter = userAdapter  // 保存引用以便更新

        userList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }

        // 获取用户列表
        ApiClient.service.getOnlineUsers().enqueue(object : Callback<List<User>> {
            override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    response.body()?.let { users ->
                        userAdapter.updateUsers(users)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "获取用户列表失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<User>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        })

        dialog.setView(view)
        dialog.show()
    }

    private fun logout() {
        UserPreferences.clear(this)
        webSocket.close(1000, "User logged out")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Activity destroyed")
    }
}