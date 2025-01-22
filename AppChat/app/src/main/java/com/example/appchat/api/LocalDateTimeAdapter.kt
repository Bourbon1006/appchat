package com.example.appchat.api

import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalDateTime

class LocalDateTimeAdapter : JsonDeserializer<LocalDateTime>, JsonSerializer<LocalDateTime> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime {
        if (json == null) {
            return LocalDateTime.now()
        }

        return when {
            json.isJsonArray -> {
                val array = json.asJsonArray
                LocalDateTime.of(
                    array[0].asInt,  // year
                    array[1].asInt,  // month
                    array[2].asInt,  // day
                    array[3].asInt,  // hour
                    array[4].asInt,  // minute
                    array[5].asInt,  // second
                    array[6].asInt   // nanosecond
                )
            }
            json.isJsonPrimitive -> {
                LocalDateTime.parse(json.asString)
            }
            else -> LocalDateTime.now()
        }
    }

    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src.toString())
    }
} 