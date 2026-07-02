package com.example.recallai.data.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * Care relationship payloads sometimes send [caregiverId] / [patientId] as either a Mongo id
 * string or a populated user object. Moshi cannot map both to [UserDto] alone.
 */
sealed class EmbeddedUserRef {
    data class Full(val user: UserDto) : EmbeddedUserRef()
    data class IdOnly(val id: String) : EmbeddedUserRef()
}

fun EmbeddedUserRef?.resolveUser(): UserDto? = when (this) {
    is EmbeddedUserRef.Full -> user
    is EmbeddedUserRef.IdOnly -> UserDto(_id = id)
    null -> null
}

class EmbeddedUserRefJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (Types.getRawType(type) != EmbeddedUserRef::class.java) return null
        val userAdapter = moshi.adapter(UserDto::class.java)
        return object : JsonAdapter<EmbeddedUserRef?>() {
            override fun fromJson(reader: JsonReader): EmbeddedUserRef? {
                return when (reader.peek()) {
                    JsonReader.Token.NULL -> {
                        reader.skipValue()
                        null
                    }
                    JsonReader.Token.STRING -> EmbeddedUserRef.IdOnly(reader.nextString())
                    JsonReader.Token.BEGIN_OBJECT -> {
                        val u = userAdapter.fromJson(reader) ?: return null
                        EmbeddedUserRef.Full(u)
                    }
                    else -> {
                        reader.skipValue()
                        null
                    }
                }
            }

            override fun toJson(writer: JsonWriter, value: EmbeddedUserRef?) {
                when (value) {
                    null -> writer.nullValue()
                    is EmbeddedUserRef.IdOnly -> writer.value(value.id)
                    is EmbeddedUserRef.Full -> userAdapter.toJson(writer, value.user)
                }
            }
        }
    }
}
