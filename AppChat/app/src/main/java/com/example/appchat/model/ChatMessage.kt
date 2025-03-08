package com.example.appchat.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDateTime

data class ChatMessage(
    val id: Long? = null,
    val content: String,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long? = null,
    val receiverName: String? = null,
    val groupId: Long? = null,
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val timestamp: LocalDateTime? = null,
    val chatType: String = if (groupId != null) "GROUP" else "PRIVATE"
)

class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
    override fun write(out: JsonWriter, value: LocalDateTime?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(input: JsonReader): LocalDateTime? {
        val value = input.nextString()
        return if (value == null) null else LocalDateTime.parse(value)
    }
}
