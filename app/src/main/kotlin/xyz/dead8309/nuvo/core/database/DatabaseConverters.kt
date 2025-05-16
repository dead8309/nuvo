package xyz.dead8309.nuvo.core.database

import android.util.Log
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.ChatMessage
import javax.inject.Inject

private const val TAG = "DatabaseConverters"

/**
 * Room TypeConverters for various types used in the database.
 */
@ProvidedTypeConverter
class DatabaseConverters @Inject constructor(private val json: Json) {
    // Instant <-> Long
    @TypeConverter
    fun longToInstant(value: Long?): Instant? {
        return value?.let(Instant::fromEpochMilliseconds)
    }

    @TypeConverter
    fun instantToLong(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    // List<ChatMessage.ToolCall> <-> JSON String
    @TypeConverter
    fun toolCallListFromJson(jsonString: String?): List<ChatMessage.ToolCall>? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            json.decodeFromString<List<ChatMessage.ToolCall>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ToolCall list from JSON: $jsonString", e)
            null
        }
    }

    @TypeConverter
    fun toolCallListToJson(toolCalls: List<ChatMessage.ToolCall>?): String? {
        if (toolCalls.isNullOrEmpty()) return null
        return try {
            json.encodeToString(toolCalls)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding ToolCall list to JSON: $toolCalls", e)
            null
        }
    }

    // ChatMessage.ToolResult <-> JSON String
    @TypeConverter
    fun toolResultFromJson(jsonString: String?): ChatMessage.ToolResult? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ChatMessage.ToolResult>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ToolResult from JSON: $jsonString", e)
            null
        }
    }

    @TypeConverter
    fun toolResultToJson(toolResult: ChatMessage.ToolResult?): String? {
        if (toolResult == null) return null
        return try {
            json.encodeToString(toolResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding ToolResult to JSON: $toolResult", e)
            null
        }
    }

    // Map<String, String> <-> JSON String (headers)
    @TypeConverter
    fun headersToJson(headers: Map<String, String>): String {
        return json.encodeToString(headers)
    }

    @TypeConverter
    fun headersFromJson(jsonString: String): Map<String, String> {
        return json.decodeFromString(jsonString)
    }

    // AuthStatus <-> JSON String
    @TypeConverter
    fun authStatusToJson(authStatus: AuthStatus): String {
        return json.encodeToString(authStatus)
    }

    @TypeConverter
    fun authStatusFromJson(jsonString: String): AuthStatus {
        return json.decodeFromString(jsonString)
    }

    // Tool.Input <-> JSON String
    @TypeConverter
    fun toolInputToJson(input: Tool.Input): String {
        return json.encodeToString(input)
    }

    @TypeConverter
    fun toolInputFromJson(jsonString: String): Tool.Input {
        return json.decodeFromString(jsonString)
    }
}