package com.example.appchat.model

data class ContactGroup(
    val id: Long,
    val name: String,
    val groupType: Int = TYPE_CUSTOM,  // 默认为自定义类型
    val creatorId: Long? = null,
    val contacts: MutableList<Contact>? = mutableListOf()  // 允许为 null
) {
    companion object {
        const val TYPE_DEFAULT = 0      // 默认分组（在线/离线）
        const val TYPE_MY_FRIENDS = 1   // 我的好友组
        const val TYPE_CUSTOM = 2       // 自定义分组
    }
}