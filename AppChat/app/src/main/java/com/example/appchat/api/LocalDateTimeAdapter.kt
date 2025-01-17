package com.example.appchat.api

import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalDateTime

class LocalDateTimeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src.toString())
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime {
        if (json is JsonArray) {
            // 处理数组格式的时间戳 [year, month, day, hour, minute, second, nano]
            return LocalDateTime.of(
                json.get(0).asInt,  // year
                json.get(1).asInt,  // month
                json.get(2).asInt,  // day
                json.get(3).asInt,  // hour
                json.get(4).asInt,  // minute
                json.get(5).asInt,  // second
                json.get(6).asInt   // nanosecond
            )
        } else {
            // 如果是字符串格式，使用 parse
            return LocalDateTime.parse(json?.asString)
        }
    }
} 