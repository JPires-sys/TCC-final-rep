package com.fabio.eagleeyes.chat

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DailyOpening(val date: String, val text: String)
data class LocalChats(
    val active: ChatSession? = null,
    val pending: List<ChatSession> = emptyList(),
    val daily: DailyOpening? = null
)

interface ChatLocalStore {
    suspend fun read(): LocalChats
    suspend fun write(chats: LocalChats)
}

// Arquivo privado por conta, fora do backup. AtomicFile mantém a última versão completa.
class FileChatLocalStore(directory: File, uid: String) : ChatLocalStore {
    private val file = AtomicFile(File(directory, "${uid.toByteArray().joinToString("") { "%02x".format(it) }}.json"))
    init { require(uid.isNotBlank()); check(directory.isDirectory || directory.mkdirs()) }

    override suspend fun read(): LocalChats = withContext(Dispatchers.IO) {
        try { ChatLocalCodec.decode(file.openRead().bufferedReader().use { it.readText() }) }
        catch (_: java.io.FileNotFoundException) { LocalChats() }
    }

    override suspend fun write(chats: LocalChats) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try {
            stream.write(ChatLocalCodec.encode(chats).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

object ChatLocalCodec {
    private fun session(value: ChatSession) = JSONObject().apply {
        put("id", value.id); put("date", value.date); put("startedAt", value.startedAt)
        put("endedAt", value.endedAt); put("draft", value.draft)
        put("messages", JSONArray().apply { value.messages.forEach { message ->
            put(JSONObject().put("id", message.id).put("role", message.role)
                .put("content", message.content).put("createdAt", message.createdAt))
        } })
    }
    private fun session(json: JSONObject) = ChatSession(
        id = json.getString("id"), date = json.getString("date"), startedAt = json.getLong("startedAt"),
        endedAt = json.optLong("endedAt"), draft = json.optString("draft"),
        messages = json.getJSONArray("messages").let { array -> (0 until array.length()).map { i ->
            val message = array.getJSONObject(i)
            ChatMessage(message.getString("id"), message.getString("role"), message.getString("content"), message.getLong("createdAt"))
        } }
    )
    fun encode(chats: LocalChats): String = JSONObject().apply {
        put("active", chats.active?.let(::session) ?: JSONObject.NULL)
        put("pending", JSONArray().apply { chats.pending.forEach { put(session(it)) } })
        put("daily", chats.daily?.let { JSONObject().put("date", it.date).put("text", it.text) } ?: JSONObject.NULL)
    }.toString()
    fun decode(text: String): LocalChats = JSONObject(text).let { json ->
        LocalChats(
            active = json.optJSONObject("active")?.let(::session),
            pending = json.getJSONArray("pending").let { array -> (0 until array.length()).map { session(array.getJSONObject(it)) } },
            daily = json.optJSONObject("daily")?.let { DailyOpening(it.getString("date"), it.getString("text")) }
        )
    }
}
