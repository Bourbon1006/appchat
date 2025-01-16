package com.example.appchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appchat.api.ApiClient
import com.example.appchat.model.LoginRequest
import com.example.appchat.model.AuthResponse
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.gson.Gson
import android.widget.TextView

class LoginActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 如果已经登录，直接进入主界面
        if (UserPreferences.getToken(this) != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerLink = findViewById(R.id.registerLink)

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ApiClient.service.login(LoginRequest(username, password))
                .enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful) {
                            response.body()?.let { authResponse ->
                                if (authResponse.userId != -1L) {
                                    UserPreferences.saveToken(this@LoginActivity, authResponse.token)
                                    UserPreferences.saveUserId(this@LoginActivity, authResponse.userId)
                                    UserPreferences.saveUsername(this@LoginActivity, authResponse.username)
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this@LoginActivity, 
                                        authResponse.message ?: "登录失败", 
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            try {
                                val errorBody = response.errorBody()?.string()
                                val errorResponse = Gson().fromJson(errorBody, AuthResponse::class.java)
                                Toast.makeText(this@LoginActivity, 
                                    errorResponse.message ?: "登录失败", 
                                    Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@LoginActivity, "登录失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                        t.printStackTrace()
                        Toast.makeText(this@LoginActivity, 
                            "网络错误: ${t.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }
} 