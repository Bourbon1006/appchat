package com.example.appchat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Contact
import kotlinx.coroutines.launch

class ContactsViewModel : ViewModel() {
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts
    
    private val _pendingRequests = MutableLiveData<Int>()
    val pendingRequests: LiveData<Int> = _pendingRequests
    
    // 加载数据，仅在需要时调用
    fun loadData(userId: Long) {
        if (_contacts.value == null) {
            loadContacts(userId)
        }
        loadPendingRequests(userId)
    }
    
    // 强制刷新数据
    fun refreshData(userId: Long) {
        loadContacts(userId)
        loadPendingRequests(userId)
    }
    
    private fun loadContacts(userId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getFriends(userId)
                if (response.isSuccessful) {
                    _contacts.postValue(response.body()?.map { user ->
                        Contact(
                            id = user.id,
                            username = user.username,
                            nickname = user.nickname,
                            avatarUrl = user.avatarUrl,
                            onlineStatus = user.onlineStatus ?: 0
                        )
                    })
                }
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }
    
    private fun loadPendingRequests(userId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getPendingRequests(userId)
                if (response.isSuccessful) {
                    _pendingRequests.postValue(response.body()?.size ?: 0)
                }
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }
} 