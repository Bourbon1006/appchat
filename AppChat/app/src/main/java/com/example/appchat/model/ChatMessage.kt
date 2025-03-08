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
    val timestamp: LocalDateTime? = null
)

class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
    override fun write(out: JsonWriter, value: LocalDateTime?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginArray()
        out.value(value.year)
        out.value(value.monthValue)
        out.value(value.dayOfMonth)
        out.value(value.hour)
        out.value(value.minute)
        out.value(value.second)
        out.value(value.nano)
        out.endArray()
    }

    override fun read(reader: JsonReader): LocalDateTime? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        reader.beginArray()
        val year = reader.nextInt()
        val month = reader.nextInt()
        val day = reader.nextInt()
        val hour = reader.nextInt()
        val minute = reader.nextInt()
        val second = reader.nextInt()
        val nano = reader.nextInt()
        reader.endArray()
        return LocalDateTime.of(year, month, day, hour, minute, second, nano)
    }
}
