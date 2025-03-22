package com.example.appchat.activity

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appchat.adapter.SelectContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.ActivitySelectContactsBinding
import com.example.appchat.model.UserDTO
import kotlinx.coroutines.launch

class SelectContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectContactsBinding
    private lateinit var adapter: SelectContactAdapter
    private var groupId: Long = -1
    private var existingMembers = setOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getLongExtra("group_id", -1)
        existingMembers = intent.getLongArrayExtra("existing_members")?.toSet() ?: setOf()

        setupToolbar()
        loadContacts()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "选择联系人"
    }

    private fun loadContacts() {
        val userId = com.example.appchat.util.UserPreferences.getUserId(this)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriends(userId)
                if (response.isSuccessful) {
                    response.body()?.let { friends ->
                        setupAdapter(friends)
                    }
                } else {
                    Toast.makeText(this@SelectContactsActivity, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SelectContactsActivity, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAdapter(contacts: List<UserDTO>) {
        adapter = SelectContactAdapter(contacts, existingMembers)
        binding.contactsRecyclerView.adapter = adapter
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        binding.btnAddSelected.setOnClickListener {
            val selectedContacts = adapter.getSelectedContactIds()
            if (selectedContacts.isEmpty()) {
                Toast.makeText(this, "请选择至少一个联系人", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addMembersToGroup(selectedContacts)
        }
    }

    private fun addMembersToGroup(memberIds: List<Long>) {
        lifecycleScope.launch {
            try {
                val results = mutableListOf<Boolean>()
                
                for (memberId in memberIds) {
                    val response = ApiClient.apiService.addGroupMember(groupId, memberId)
                    results.add(response.isSuccessful)
                }
                
                val successCount = results.count { it }
                
                if (successCount == memberIds.size) {
                    Toast.makeText(this@SelectContactsActivity, "成功添加所有成员", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@SelectContactsActivity, "成功添加 $successCount/${memberIds.size} 名成员", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SelectContactsActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 