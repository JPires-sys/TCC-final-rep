package com.fabio.eagleeyes.chat

import com.fabio.eagleeyes.global.FirebaseConfig
import kotlinx.coroutines.tasks.await

data class ChatMessage(val id: String = "", val role: String = "", val content: String = "", val createdAt: Long = 0)
data class ChatSession(
    val id: String = "", val date: String = "", val startedAt: Long = 0,
    val endedAt: Long = 0, val messages: List<ChatMessage> = emptyList(), val draft: String = ""
)
data class ChatHistoryPage(val sessions: List<ChatSession>, val olderThan: String?)

interface ChatHistoryStore {
    suspend fun save(session: ChatSession)
    suspend fun load(beforeDate: String? = null): ChatHistoryPage
}

class ChatHistoryRepository(private val uid: String) : ChatHistoryStore {
    private val root get() = FirebaseConfig.getDatabase().child("historicoChat").child(uid)
    private fun checkSession() {
        check(uid.isNotBlank() && FirebaseConfig.getAuth().currentUser?.uid == uid) {
            "Entre novamente na conta do jovem para acessar suas conversas."
        }
    }

    override suspend fun save(session: ChatSession) {
        checkSession()
        require(session.id.isNotBlank() && session.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(session.endedAt > 0)
        // Escrita da conversa completa; a mesma chave torna as tentativas idempotentes.
        root.child(session.date).child(session.id).setValue(mapOf(
            "id" to session.id, "date" to session.date, "startedAt" to session.startedAt,
            "endedAt" to session.endedAt,
            "mensagens" to session.messages.associate { message -> message.id to mapOf(
                "id" to message.id, "role" to message.role,
                "content" to message.content, "createdAt" to message.createdAt
            ) }
        )).await()
        checkSession()
    }

    override suspend fun load(beforeDate: String?): ChatHistoryPage {
        checkSession()
        var query = root.orderByKey()
        if (beforeDate != null) query = query.endBefore(beforeDate)
        val snapshot = query.limitToLast(30).get().await()
        val sessions = snapshot.children.flatMap { day -> day.children.map { item ->
            ChatSession(
                id = requireNotNull(item.key), date = requireNotNull(day.key),
                startedAt = item.child("startedAt").getValue(Long::class.java) ?: 0,
                endedAt = item.child("endedAt").getValue(Long::class.java) ?: 0,
                messages = item.child("mensagens").children.mapNotNull { it.getValue(ChatMessage::class.java) }
                    .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
            )
        } }
        checkSession()
        return ChatHistoryPage(sessions.sortedByDescending { it.startedAt },
            if (snapshot.childrenCount == 30L) snapshot.children.first().key else null)
    }
}
