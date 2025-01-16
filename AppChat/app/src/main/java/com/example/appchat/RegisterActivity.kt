package com.example.appchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appchat.api.ApiClient
import com.example.appchat.model.AuthResponse
import com.example.appchat.model.RegisterRequest
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        setupViews()
    }

    private fun setupViews() {
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        loginLink = findViewById(R.id.loginLink)

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

            ApiClient.service.register(RegisterRequest(username, password))
                .enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(
                        call: Call<AuthResponse>,
                        response: Response<AuthResponse>
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let { authResponse ->
                                if (authResponse.userId != -1L) {
                                    UserPreferences.saveToken(this@RegisterActivity, authResponse.token)
                                    UserPreferences.saveUserId(this@RegisterActivity, authResponse.userId)
                                    UserPreferences.saveUsername(this@RegisterActivity, authResponse.username)
                                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this@RegisterActivity, 
                                        authResponse.message ?: "注册失败", 
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(this@RegisterActivity, "注册失败", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                        Toast.makeText(this@RegisterActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
} 