package com.example.appchat.activity

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
import android.view.View
import android.widget.ProgressBar
import com.example.appchat.R

class LoginActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 初始化视图
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            loginButton.isEnabled = false

            performLogin(username, password)
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin(username: String, password: String) {
        val request = LoginRequest(username, password)
        ApiClient.apiService.login(request).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true

                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        handleLoginSuccess(authResponse)
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "登录失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                Toast.makeText(this@LoginActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun handleLoginSuccess(response: AuthResponse) {
        // 使用现有的 saveUserData 方法保存所有用户信息
        UserPreferences.saveUserData(
            context = this,
            userId = response.userId,
            token = response.token,
            username = response.username
        )
        
        // 打印日志确认
        println("✅ Login success - userId: ${response.userId}")
        
        // 启动主页面
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        // 添加过渡动画
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
} 