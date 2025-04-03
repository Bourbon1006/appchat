package com.example.appchat.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDateTime

data class ChatMessage(
    val id: Long?,
    val senderId: Long,
    val senderName: String,
    val content: String,
    val type: MessageType,
    val receiverId: Long?,
    val receiverName: String?,
    val groupId: Long?,
    val groupName: String?,
    val timestamp: LocalDateTime?,
    val fileUrl: String?,
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
