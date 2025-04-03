package com.example.appchat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        const val DATABASE_NAME = "chat.db"
        const val DATABASE_VERSION = 3
        
        // 表名
        const val TABLE_MESSAGES = "messages"
        const val TABLE_USERS = "users"
        
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
                receiver_name TEXT,
                group_id INTEGER,
                group_name TEXT,
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
} 