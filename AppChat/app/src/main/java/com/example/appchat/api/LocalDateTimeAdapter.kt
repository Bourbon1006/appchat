package com.example.appchat.api

import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDateTimeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime {
        return when {
            json?.isJsonArray == true -> {
                // 处理数组格式的时间戳
                val arr = json.asJsonArray
                LocalDateTime.of(
                    arr.get(0).asInt, // year
                    arr.get(1).asInt, // month
                    arr.get(2).asInt, // day
                    arr.get(3).asInt, // hour
                    arr.get(4).asInt, // minute
                    arr.get(5).asInt, // second
                    arr.get(6).asInt  // nanosecond
                )
            }
            json?.isJsonPrimitive == true -> {
                // 处理字符串格式的时间戳
                LocalDateTime.parse(json.asString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            else -> throw JsonParseException("Invalid timestamp format")
        }
    }
} 