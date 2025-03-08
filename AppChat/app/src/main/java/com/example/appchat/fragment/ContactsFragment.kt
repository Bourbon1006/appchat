package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.ChatActivity
import com.example.appchat.MainActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ContactsFragment : Fragment() {
    private lateinit var contactsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var adapter: ContactAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)

        contactsList = view.findViewById(R.id.contactsList)
        progressBar = view.findViewById(R.id.progressBar)
        searchInput = view.findViewById(R.id.searchInput)
        searchButton = view.findViewById(R.id.searchButton)

        setupRecyclerView()
        loadContacts()
        setupSearchButton()

        return view
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter { contact ->
            // 使用 Intent 启动 ChatActivity
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("receiver_id", contact.id)
                putExtra("receiver_name", contact.username)
            }
            startActivity(intent)
        }
        contactsList.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@ContactsFragment.adapter
        }
    }

    private fun loadContacts() {
        progressBar.visibility = View.VISIBLE
        ApiClient.apiService.getUserContacts(UserPreferences.getUserId(requireContext()))
            .enqueue(object : Callback<List<UserDTO>> {
                override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        response.body()?.let { users ->
                            adapter.updateContacts(users)
                        }
                    }
                }

                override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupSearchButton() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 实现搜索功能
                searchUsers(query)
            }
        }
    }

    private fun searchUsers(query: String) {
        progressBar.visibility = View.VISIBLE
        // 这里需要实现搜索用户的API调用
        // 暂时使用加载所有联系人的方式
        loadContacts()
    }
}