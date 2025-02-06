package com.example.appchat.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.appchat.model.ChatMessage
import com.example.appchat.model.MessageType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "chat.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_USERS = "users"
        
        private const val COLUMN_ID = "id"
        private const val COLUMN_SENDER_ID = "sender_id"
        private const val COLUMN_SENDER_NAME = "sender_name"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_FILE_URL = "file_url"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_CHAT_TYPE = "chat_type"
        private const val COLUMN_RECEIVER_ID = "receiver_id"
        private const val COLUMN_GROUP_ID = "group_id"
        private const val COLUMN_MESSAGE_ID = "id"
        private const val COLUMN_DELETED_BY = "deleted_by"

        // 创建消息表的 SQL
        private const val CREATE_MESSAGES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                id INTEGER PRIMARY KEY,
                sender_id INTEGER,
                sender_name TEXT,
                content TEXT,
                type TEXT,
                file_url TEXT,
                timestamp TEXT,
                chat_type TEXT,
                receiver_id INTEGER,
                group_id INTEGER,
                deleted_by TEXT DEFAULT ''
            )
        """

        // 创建用户表的 SQL
        private const val CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                avatar_url TEXT,
                is_online INTEGER DEFAULT 0
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建所有表
        db.execSQL(CREATE_MESSAGES_TABLE)
        db.execSQL(CREATE_USERS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // 添加 deleted_by 列
            try {
                db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN deleted_by TEXT DEFAULT ''")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveMessage(message: ChatMessage, chatType: String, receiverId: Long? = null, groupId: Long? = null) {
        val db = this.writableDatabase
        try {
            db.beginTransaction()
            
            val values = ContentValues().apply {
                message.id?.let { put(COLUMN_ID, it) }
                put(COLUMN_SENDER_ID, message.senderId)
                put(COLUMN_SENDER_NAME, message.senderName)
                put(COLUMN_CONTENT, message.content)
                put(COLUMN_TYPE, message.type.name)
                put(COLUMN_FILE_URL, message.fileUrl)
                put(COLUMN_TIMESTAMP, message.timestamp?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                put(COLUMN_CHAT_TYPE, chatType)
                receiverId?.let { put(COLUMN_RECEIVER_ID, it) }
                groupId?.let { put(COLUMN_GROUP_ID, it) }
            }

            val result = db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            
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

    fun deleteMessage(messageId: Long) {
        writableDatabase.delete(
            TABLE_MESSAGES,
            "$COLUMN_MESSAGE_ID = ?",
            arrayOf(messageId.toString())
        )
    }

    fun markMessageAsDeleted(messageId: Long, userId: Long) {
        val cursor = writableDatabase.query(
            TABLE_MESSAGES,
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

            writableDatabase.update(
                TABLE_MESSAGES,
                values,
                "id = ?",
                arrayOf(messageId.toString())
            )
        }
        cursor.close()
    }

    fun isMessageDeletedForUser(messageId: Long, userId: Long): Boolean {
        val cursor = readableDatabase.query(
            TABLE_MESSAGES,
            arrayOf("deleted_by"),
            "id = ?",
            arrayOf(messageId.toString()),
            null,
            null,
            null
        )

        return if (cursor.moveToFirst()) {
            val deletedBy = cursor.getString(0) ?: ""
            val deletedUsers = deletedBy.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
            cursor.close()
            userId in deletedUsers
        } else {
            cursor.close()
            false
        }
    }

    fun getMessages(): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        with(cursor) {
            while (moveToNext()) {
                val message = ChatMessage(
                    id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
                    senderId = getLong(getColumnIndexOrThrow(COLUMN_SENDER_ID)),
                    senderName = getString(getColumnIndexOrThrow(COLUMN_SENDER_NAME)),
                    content = getString(getColumnIndexOrThrow(COLUMN_CONTENT)),
                    type = MessageType.valueOf(getString(getColumnIndexOrThrow(COLUMN_TYPE))),
                    fileUrl = getString(getColumnIndexOrThrow(COLUMN_FILE_URL)),
                    timestamp = LocalDateTime.parse(
                        getString(getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    )
                )
                messages.add(message)
            }
        }
        cursor.close()
        db.close()
        return messages
    }

    fun clearMessages() {
        val db = this.writableDatabase
        db.delete(TABLE_MESSAGES, null, null)
        db.close()
    }

    fun getPrivateMessages(userId1: Long, userId2: Long): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val cursor = readableDatabase.query(
            TABLE_MESSAGES,
            null,
            "chat_type = 'private' AND ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))",
            arrayOf(userId1.toString(), userId2.toString(), userId2.toString(), userId1.toString()),
            null,
            null,
            "timestamp ASC"
        )

        while (cursor.moveToNext()) {
            val messageId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            if (!isMessageDeletedForUser(messageId, userId1)) {
                messages.add(createMessageFromCursor(cursor))
            }
        }
        cursor.close()
        return messages
    }

    fun getGroupMessages(groupId: Long): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val db = this.readableDatabase
        val selection = "$COLUMN_CHAT_TYPE = 'group' AND $COLUMN_GROUP_ID = ?"
        val selectionArgs = arrayOf(groupId.toString())
        
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                messages.add(createMessageFromCursor(it))
            }
        }
        db.close()
        return messages
    }

    private fun createMessageFromCursor(cursor: android.database.Cursor): ChatMessage {
        return ChatMessage(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            senderId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SENDER_ID)),
            senderName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER_NAME)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
            type = MessageType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE))),
            fileUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_URL)),
            timestamp = LocalDateTime.parse(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
        )
    }

    fun clearPrivateMessages(userId1: Long, userId2: Long) {
        val db = this.writableDatabase
        val whereClause = """
            $COLUMN_CHAT_TYPE = 'private' AND 
            (($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?) OR 
            ($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?))
        """.trimIndent()
        val whereArgs = arrayOf(
            userId1.toString(), userId2.toString(),
            userId2.toString(), userId1.toString()
        )
        db.delete(TABLE_MESSAGES, whereClause, whereArgs)
        db.close()
    }

    fun clearGroupMessages(groupId: Long) {
        val db = this.writableDatabase
        val whereClause = "$COLUMN_CHAT_TYPE = 'group' AND $COLUMN_GROUP_ID = ?"
        val whereArgs = arrayOf(groupId.toString())
        db.delete(TABLE_MESSAGES, whereClause, whereArgs)
        db.close()
    }

    // 添加一个方法来验证消息是否存在
    fun isMessageExists(messageId: Long): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf(COLUMN_ID),
            "$COLUMN_ID = ?",
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
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("avatar_url", avatarUrl)
            }
            db.update(TABLE_USERS, values, "id = ?", arrayOf(userId.toString()))
        }
    }

    // 获取用户头像URL的方法
    fun getUserAvatarUrl(userId: Long): String? {
        readableDatabase.use { db ->
            val cursor = db.query(
                TABLE_USERS,
                arrayOf("avatar_url"),
                "id = ?",
                arrayOf(userId.toString()),
                null,
                null,
                null
            )
            return cursor.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow("avatar_url"))
                } else {
                    null
                }
            }
        }
    }
} 