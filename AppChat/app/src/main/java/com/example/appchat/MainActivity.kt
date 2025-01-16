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
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.User
import com.example.appchat.util.UserPreferences
import com.google.gson.Gson
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var webSocket: WebSocket
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var toolbar: Toolbar
    private val gson = Gson()
    private var currentChatUserId: Long? = null

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
                val chatMessage = ChatMessage(
                    senderId = UserPreferences.getUserId(this),
                    senderName = UserPreferences.getUsername(this) ?: "Unknown",
                    receiverId = currentChatUserId,
                    receiverName = null,
                    content = message,
                    timestamp = LocalDateTime.now()
                )
                webSocket.send(gson.toJson(chatMessage))
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
                    val message = gson.fromJson(text, ChatMessage::class.java)
                    messageAdapter.addMessage(message)
                    messageList.scrollToPosition(messageAdapter.itemCount - 1)
                }
            }
        })
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
            title = "与 ${user.name} 聊天中"
            messageAdapter.clearMessages()
            dialog.dismiss()
        }

        userList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = userAdapter
        }

        // 获取用户列表
        ApiClient.service.getUsers().enqueue(object : Callback<List<User>> {
            override fun onResponse(
                call: Call<List<User>>,
                response: Response<List<User>>
            ) {
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