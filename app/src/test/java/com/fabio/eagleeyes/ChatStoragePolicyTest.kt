package com.fabio.eagleeyes

import com.fabio.eagleeyes.chat.*
import org.junit.Assert.*
import org.junit.Test

class ChatStoragePolicyTest {
    @Test fun localCodecPreservesMessagesDraftPendingSessionsAndDailyOpening() {
        val session = ChatSession("abc", "2026-10-16", 10, 20,
            listOf(ChatMessage("00000000", "user", "Olá\n\"teste\" 😀", 11)), "Rascunho")
        val chats = LocalChats(session, listOf(session.copy(id = "pending")), DailyOpening("2026-10-16", "Como foi?"))
        assertEquals(chats, ChatLocalCodec.decode(ChatLocalCodec.encode(chats)))
        assertEquals(LocalChats(), ChatLocalCodec.decode(ChatLocalCodec.encode(LocalChats())))
    }

    @Test fun usesScreenTimeOnceEveryThreeDaysAndIsStableWithinTheDay() {
        for (uid in listOf("jovem-a", "jovem-b", "negative-hash-user")) {
            val dates = (1..30).map { "2026-10-${it.toString().padStart(2, '0')}" }
            assertEquals(10, dates.count { DailyQuestionPolicy.useUsage(it, uid) })
            dates.forEach { assertEquals(DailyQuestionPolicy.useUsage(it, uid), DailyQuestionPolicy.useUsage(it, uid)) }
        }
    }
}
