package com.example.appchat.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appchat.R
import com.example.appchat.api.ApiClient
import com.example.appchat.model.AuthResponse
import com.example.appchat.model.RegisterRequest
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)

        registerButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            registerButton.isEnabled = false

            performRegister(username, password)
        }
    }

    private fun performRegister(username: String, password: String) {
        val request = RegisterRequest(
            username = username,
            password = password
        )
        
        ApiClient.apiService.register(request).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                progressBar.visibility = View.GONE
                registerButton.isEnabled = true
                
                if (response.isSuccessful) {
                    response.body()?.let { authResponse ->
                        // 保存用户信息
                        UserPreferences.saveUserData(
                            this@RegisterActivity,
                            authResponse.userId,
                            authResponse.token,
                            authResponse.username
                        )
                    }
                    Toast.makeText(this@RegisterActivity, "注册成功", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    try {
                        // 解析错误信息
                        val errorBody = response.errorBody()?.string()
                        val errorJson = JSONObject(errorBody ?: "")
                        val errorMessage = errorJson.optString("message", "注册失败")
                        Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@RegisterActivity, "注册失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                registerButton.isEnabled = true
                Toast.makeText(this@RegisterActivity, "网络错误: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
} 