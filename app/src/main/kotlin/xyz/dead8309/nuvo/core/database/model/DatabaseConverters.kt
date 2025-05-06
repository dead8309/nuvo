package xyz.dead8309.nuvo.core.database.model

import android.util.Log
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.ChatMessage
import javax.inject.Inject


/**
 * Room TypeConverter for kotlinx.datetime.Instant <-> Long (Epoch Milliseconds).
 */
@ProvidedTypeConverter
class InstantConverter @Inject constructor() {
    @TypeConverter
    fun longToInstant(value: Long?): Instant? {
        return value?.let(Instant::fromEpochMilliseconds)
    }

    @TypeConverter
    fun instantToLong(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }
}


/**
 * Room TypeConverter for List<ChatMessage.ToolCall> <-> JSON String.
 * Requires a Json instance provided via Hilt (using @ProvidedTypeConverter).
 */
@ProvidedTypeConverter
class ToolCallListConverter @Inject constructor(private val json: Json) {
    private val TAG = "ToolCallListConverter"

    @TypeConverter
    fun fromJson(jsonString: String?): List<ChatMessage.ToolCall>? {
        if (jsonString.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString<List<ChatMessage.ToolCall>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ToolCall list from JSON: $jsonString", e)
            null
        }
    }

    @TypeConverter
    fun toJson(toolCalls: List<ChatMessage.ToolCall>?): String? {
        if (toolCalls.isNullOrEmpty()) {
            return null
        }
        return try {
            json.encodeToString(toolCalls)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding ToolCall list to JSON: $toolCalls", e)
            null
        }
    }
}


/**
 * Room TypeConverter for ChatMessage.ToolResult <-> JSON String.
 * Requires a Json instance provided via Hilt (using @ProvidedTypeConverter).
 */
@ProvidedTypeConverter
class ToolResultConverter @Inject constructor(private val json: Json) {
    private val TAG = "ToolResultConverter"

    @TypeConverter
    fun fromJson(jsonString: String?): ChatMessage.ToolResult? {
        if (jsonString.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString<ChatMessage.ToolResult>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ToolResult from JSON: $jsonString", e)
            null
        }
    }

    @TypeConverter
    fun toJson(toolResult: ChatMessage.ToolResult?): String? {
        if (toolResult == null) {
            return null
        }
        return try {
            json.encodeToString(toolResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding ToolResult to JSON: $toolResult", e)
            null
        }
    }
}

@ProvidedTypeConverter
class McpServerHeadersConverter @Inject constructor(private val json: Json) {
    @TypeConverter
    fun toJson(headers: Map<String, String>): String {
        return json.encodeToString(headers)
    }

    @TypeConverter
    fun fromJson(jsonString: String): Map<String, String> {
        return json.decodeFromString(jsonString)
    }
}

@ProvidedTypeConverter
class McpServerAuthStatusConverter @Inject constructor(private val json: Json) {
    @TypeConverter
    fun toJson(authStatus: AuthStatus): String {
        return json.encodeToString(authStatus)
    }

    @TypeConverter
    fun fromJson(jsonString: String): AuthStatus {
        return json.decodeFromString(jsonString)
    }
}