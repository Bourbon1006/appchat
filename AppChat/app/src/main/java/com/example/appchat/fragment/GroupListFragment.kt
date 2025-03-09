package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.ChatActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactSelectionAdapter
import com.example.appchat.adapter.GroupAdapter
import com.example.appchat.model.CreateGroupRequest
import com.example.appchat.model.Group
import com.example.appchat.model.UserDTO
import com.example.appchat.api.ApiClient
import com.example.appchat.util.UserPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GroupListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroupAdapter
    private lateinit var createGroupButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_group_list, container, false)
        
        recyclerView = view.findViewById(R.id.groupsList)
        createGroupButton = view.findViewById(R.id.createGroupButton)
        
        setupRecyclerView()
        setupCreateGroupButton()
        loadGroups()
        
        return view
    }

    private fun setupRecyclerView() {
        adapter = GroupAdapter { group ->
            println("🚀 Starting group chat - GroupID: ${group.id}, Name: ${group.name}")
            
            startActivity(Intent(context, ChatActivity::class.java).apply {
                putExtra("chat_type", "GROUP")
                putExtra("group_id", group.id)
                putExtra("group_name", group.name)
            })
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupCreateGroupButton() {
        createGroupButton.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.groupNameInput)
        val contactsList = dialogView.findViewById<RecyclerView>(R.id.contactsList)
        val createButton = dialogView.findViewById<Button>(R.id.createButton)

        // 设置联系人列表
        val contactsAdapter = ContactSelectionAdapter()
        contactsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = contactsAdapter
        }

        // 加载联系人
        ApiClient.apiService.getUserContacts(UserPreferences.getUserId(requireContext()))
            .enqueue(object : Callback<List<UserDTO>> {
                override fun onResponse(call: Call<List<UserDTO>>, response: Response<List<UserDTO>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            contactsAdapter.updateContacts(contacts)
                        }
                    }
                }

                override fun onFailure(call: Call<List<UserDTO>>, t: Throwable) {
                    Toast.makeText(context, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("创建群组")
            .setView(dialogView)
            .create()

        createButton.setOnClickListener {
            val groupName = nameInput.text.toString().trim()
            val selectedMembers = contactsAdapter.getSelectedContacts()
            
            if (groupName.isNotEmpty() && selectedMembers.isNotEmpty()) {
                val request = CreateGroupRequest(
                    name = groupName,
                    creatorId = UserPreferences.getUserId(requireContext()),
                    memberIds = selectedMembers.map { it.id }
                )

                ApiClient.apiService.createGroup(request)
                    .enqueue(object : Callback<Group> {
                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "群组创建成功", Toast.LENGTH_SHORT).show()
                                loadGroups() // 重新加载群组列表
                                dialog.dismiss()
                            } else {
                                Toast.makeText(context, "创建群组失败", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<Group>, t: Throwable) {
                            Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                        }
                    })
            } else {
                Toast.makeText(context, "请输入群组名称并选择成员", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun loadGroups() {
        ApiClient.apiService.getUserGroups(UserPreferences.getUserId(requireContext()))
            .enqueue(object : Callback<List<Group>> {
                override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { groups ->
                            adapter.updateGroups(groups)
                        }
                    } else {
                        Toast.makeText(context, "加载群组失败", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                    Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                }
            })
    }
} 