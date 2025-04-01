package com.example.appchat.model

data class ContactGroup(
    val id: Long,
    val name: String,
    val contacts: MutableList<Contact> = mutableListOf(),
    var isExpanded: Boolean = true,  // 控制分组是否展开
    val creatorId: Long? = null,     // 创建者ID，null表示系统默认分组
    val groupType: Int = TYPE_CUSTOM // 分组类型
) {
    companion object {
        const val TYPE_DEFAULT = 0  // 系统默认分组（在线/离线）
        const val TYPE_MY_FRIENDS = 1  // "我的好友"默认分组
        const val TYPE_CUSTOM = 2  // 用户自定义分组
    }
}