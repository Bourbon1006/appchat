package com.example.appchat.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatDatabase(private val context: Context) {
    private val dbHelper = ChatDatabaseHelper(context)

    fun saveMessage(message: ChatMessage, chatType: String, receiverId: Long? = null, groupId: Long? = null) {
        val db = dbHelper.writableDatabase
        try {
            db.beginTransaction()
            
            val values = ContentValues().apply {
                message.id?.let { put("id", it) }
                put("sender_id", message.senderId)
                put("sender_name", message.senderName)
                put("content", message.content)
                put("type", message.type.name)
                put("file_url", message.fileUrl)
                put("timestamp", message.timestamp?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                put("chat_type", chatType)
                receiverId?.let { put("receiver_id", it) }
                groupId?.let { put("group_id", it) }
            }

            val result = db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            
            if (result != -1L) {
                db.setTransactionSuccessful()
                println("Message saved successfully to local database:")
                println("ID: ${message.id}")
                println("Content: ${message.content}")
                println("Sender: ${message.senderName}")
                println("Chat Type: $chatType")
                println("Receiver ID: $receiverId")
                println("Group ID: $groupId")
            } else {
                println("Failed to save message to local database")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error saving message to local database: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun deleteMessage(messageId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rowsAffected = db.delete(
            "messages",
            "id = ?",
            arrayOf(messageId.toString())
        )
        db.close()
        return rowsAffected > 0 // 返回是否成功删除
    }

    fun markMessageAsDeleted(messageId: Long, userId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val cursor = db.query(
            "messages",
            arrayOf("deleted_by"),
            "id = ?",
            arrayOf(messageId.toString()),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            val currentDeletedBy = cursor.getString(0) ?: ""
            val newDeletedBy = if (currentDeletedBy.isEmpty()) {
                userId.toString()
            } else {
                "$currentDeletedBy,$userId"
            }

            val values = ContentValues().apply {
                put("deleted_by", newDeletedBy)
            }

            db.update(
                "messages",
                values,
                "id = ?",
                arrayOf(messageId.toString())
            )
        }
        cursor.close()
        db.close()
        return true
    }

    fun isMessageDeletedForUser(messageId: Long, userId: Long): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            arrayOf("deleted_by"),
            "id = ?",
            arrayOf(messageId.toString()),
            null,
            null,
            null
        )

        val result = if (cursor.moveToFirst()) {
            val deletedBy = cursor.getString(0) ?: ""
            val deletedUsers = deletedBy.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
            userId in deletedUsers
        } else {
            false
        }
        
        cursor.close()
        db.close()
        return result
    }

    fun clearMessages() {
        val db = dbHelper.writableDatabase
        db.delete("messages", null, null)
        db.close()
    }

    fun getPrivateMessages(userId: Long, partnerId: Long): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val db = dbHelper.readableDatabase
        
        val selection = "(sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)"
        val selectionArgs = arrayOf(
            userId.toString(), partnerId.toString(),
            partnerId.toString(), userId.toString()
        )
        
        val cursor = db.query(
            "messages",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "timestamp ASC"
        )
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val senderId = cursor.getLong(cursor.getColumnIndexOrThrow("sender_id"))
            val senderName = cursor.getString(cursor.getColumnIndexOrThrow("sender_name"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val typeStr = cursor.getString(cursor.getColumnIndexOrThrow("type"))
            val receiverId = cursor.getLong(cursor.getColumnIndexOrThrow("receiver_id"))
            val receiverName = cursor.getString(cursor.getColumnIndexOrThrow("receiver_name"))
            val timestampStr = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
            val fileUrl = cursor.getString(cursor.getColumnIndexOrThrow("file_url"))
            
            val timestamp = try {
                LocalDateTime.parse(timestampStr)
            } catch (e: Exception) {
                LocalDateTime.now()
            }
            
            val message = ChatMessage(
                id = id,
                senderId = senderId,
                senderName = senderName,
                content = content,
                type = MessageType.valueOf(typeStr),
                receiverId = receiverId,
                receiverName = receiverName,
                groupId = null,
                groupName = null,
                timestamp = timestamp,
                fileUrl = fileUrl,
                chatType = "PRIVATE"
            )
            
            messages.add(message)
        }
        
        cursor.close()
        db.close()
        
        return messages
    }

    fun getGroupMessages(groupId: Long): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val db = dbHelper.readableDatabase
        
        val selection = "group_id = ?"
        val selectionArgs = arrayOf(groupId.toString())
        
        val cursor = db.query(
            "messages",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "timestamp ASC"
        )
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val senderId = cursor.getLong(cursor.getColumnIndexOrThrow("sender_id"))
            val senderName = cursor.getString(cursor.getColumnIndexOrThrow("sender_name"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val typeStr = cursor.getString(cursor.getColumnIndexOrThrow("type"))
            val timestampStr = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
            val fileUrl = cursor.getString(cursor.getColumnIndexOrThrow("file_url"))
            val groupName = cursor.getString(cursor.getColumnIndexOrThrow("group_name"))
            
            val timestamp = try {
                LocalDateTime.parse(timestampStr)
            } catch (e: Exception) {
                LocalDateTime.now()
            }
            
            val message = ChatMessage(
                id = id,
                senderId = senderId,
                senderName = senderName,
                content = content,
                type = MessageType.valueOf(typeStr),
                receiverId = null,
                receiverName = null,
                groupId = groupId,
                groupName = groupName,
                timestamp = timestamp,
                fileUrl = fileUrl,
                chatType = "GROUP"
            )
            
            messages.add(message)
        }
        
        cursor.close()
        db.close()
        
        return messages
    }

    fun clearPrivateMessages(userId1: Long, userId2: Long) {
        val db = dbHelper.writableDatabase
        val whereClause = """
            chat_type = 'private' AND 
            ((sender_id = ? AND receiver_id = ?) OR 
            (sender_id = ? AND receiver_id = ?))
        """.trimIndent()
        val whereArgs = arrayOf(
            userId1.toString(), userId2.toString(),
            userId2.toString(), userId1.toString()
        )
        db.delete("messages", whereClause, whereArgs)
        db.close()
    }

    fun clearGroupMessages(groupId: Long) {
        val db = dbHelper.writableDatabase
        val whereClause = "chat_type = 'group' AND group_id = ?"
        val whereArgs = arrayOf(groupId.toString())
        db.delete("messages", whereClause, whereArgs)
        db.close()
    }

    // 添加一个方法来验证消息是否存在
    fun isMessageExists(messageId: Long): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            arrayOf("id"),
            "id = ?",
            arrayOf(messageId.toString()),
            null,
            null,
            null
        )
        
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    // 添加更新用户头像的方法
    fun updateUserAvatar(userId: Long, avatarUrl: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("avatar_url", avatarUrl)
        }
        db.update("users", values, "id = ?", arrayOf(userId.toString()))
        db.close()
    }

    // 获取用户头像URL的方法
    fun getUserAvatarUrl(userId: Long): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "users",
            arrayOf("avatar_url"),
            "id = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        )
        
        val result = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow("avatar_url"))
        } else {
            null
        }
        
        cursor.close()
        db.close()
        return result
    }

    fun insertMessage(message: ChatMessage): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("sender_id", message.senderId)
            put("sender_name", message.senderName)
            put("content", message.content)
            put("type", message.type.toString())
            put("timestamp", message.timestamp.toString())
            
            // 根据消息类型设置不同的字段
            if (message.chatType == "PRIVATE") {
                put("receiver_id", message.receiverId)
                put("receiver_name", message.receiverName)
                putNull("group_id")
                putNull("group_name")
            } else {
                putNull("receiver_id")
                putNull("receiver_name")
                put("group_id", message.groupId)
                put("group_name", message.groupName)
            }
            
            put("file_url", message.fileUrl)
            put("chat_type", message.chatType)
        }
        
        val id = db.insert("messages", null, values)
        db.close()
        return id
    }
} 