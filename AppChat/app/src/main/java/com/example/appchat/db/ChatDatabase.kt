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
        private const val DATABASE_VERSION = 1
        private const val TABLE_MESSAGES = "messages"
        
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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_SENDER_ID INTEGER,
                $COLUMN_SENDER_NAME TEXT,
                $COLUMN_CONTENT TEXT,
                $COLUMN_TYPE TEXT,
                $COLUMN_FILE_URL TEXT,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_CHAT_TYPE TEXT,
                $COLUMN_RECEIVER_ID INTEGER,
                $COLUMN_GROUP_ID INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
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
        val db = this.writableDatabase
        try {
            // 使用事务来确保删除操作的原子性
            db.beginTransaction()
            
            // 先检查消息是否存在
            val cursor = db.query(
                TABLE_MESSAGES,
                arrayOf(COLUMN_ID),
                "$COLUMN_ID = ?",
                arrayOf(messageId.toString()),
                null,
                null,
                null
            )
            
            if (cursor.count > 0) {
                // 删除指定 ID 的消息
                val result = db.delete(
                    TABLE_MESSAGES,
                    "$COLUMN_ID = ?",
                    arrayOf(messageId.toString())
                )
                
                if (result > 0) {
                    db.setTransactionSuccessful()
                    println("✅ Message deleted successfully from local database: $messageId")
                } else {
                    println("❌ Failed to delete message from local database: $messageId")
                }
            } else {
                println("⚠️ Message not found in local database: $messageId")
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error deleting message from local database: ${e.message}")
        } finally {
            try {
                db.endTransaction()
                db.close()
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error closing database: ${e.message}")
            }
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
        val db = this.readableDatabase
        val selection = """
            ($COLUMN_CHAT_TYPE = 'private' AND 
            (($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?) OR 
            ($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?)))
        """.trimIndent()
        val selectionArgs = arrayOf(
            userId1.toString(), userId2.toString(),
            userId2.toString(), userId1.toString()
        )
        
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
} 