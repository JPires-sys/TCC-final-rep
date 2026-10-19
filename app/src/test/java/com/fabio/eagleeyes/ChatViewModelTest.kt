package com.fabio.eagleeyes

import com.fabio.eagleeyes.chat.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class Disk : ChatLocalStore {
        var data = LocalChats()
        var fail = false
        var corrupt = false
        override suspend fun read(): LocalChats {
            if (corrupt) error("Arquivo inválido")
            return data
        }
        override suspend fun write(chats: LocalChats) {
            if (fail) error("Disco cheio")
            data = ChatLocalCodec.decode(ChatLocalCodec.encode(chats))
        }
    }
    private class Remote : ChatHistoryStore {
        val saved = linkedMapOf<String, ChatSession>()
        var attempts = 0
        var reads = 0
        var fail = false
        var failAfterWrite = false
        override suspend fun save(session: ChatSession) {
            attempts++
            if (fail) error("Sem conexão")
            saved[session.id] = session
            if (failAfterWrite) error("Confirmação perdida")
        }
        override suspend fun load(beforeDate: String?): ChatHistoryPage {
            reads++
            if (fail) error("Sem conexão")
            return ChatHistoryPage(saved.values.filter { beforeDate == null || it.date < beforeDate }, null)
        }
    }
    private inner class Fixture {
        val disk = Disk()
        val remote = Remote()
        var time = 1_760_620_000_000L
        var calls = 0
        var usageCalls = 0
        var useContext = false
        var usageFails = false
        var usageStalls = false
        var apiFails = false
        var stallAnswer = false
        var signedIn = true
        val requests = mutableListOf<List<Message>>()
        fun model() = ChatViewModel(
            ChatResponder { messages ->
                calls++
                requests.add(messages)
                if (apiFails) throw ChatException("IA indisponível")
                if (stallAnswer && messages.last().content == "Meu dia") awaitCancellation()
                "Resposta $calls"
            }, remote, disk,
            ChatUsageStore {
                usageCalls++
                if (usageStalls) awaitCancellation()
                if (usageFails) error("Sem permissão")
                "com.roblox.client: 120 min"
            }, "jovem", now = { time }, contextualDay = { _, _ -> useContext }, currentAccount = { signedIn }
        )
    }

    @Test fun savesEveryMessageLocallyButUploadsOnlyWhenExiting() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        model.send("Meu dia")
        advanceUntilIdle()
        assertEquals(3, f.disk.data.active!!.messages.size)
        assertEquals(0, f.remote.attempts)
        assertEquals(1, f.remote.reads)
        model.exit()
        advanceUntilIdle()
        assertTrue(model.state.value.closed)
        val session = f.remote.saved.values.single()
        assertEquals(chatDate(f.time), session.date)
        assertEquals(model.state.value.messages, session.messages)
        assertTrue(session.endedAt >= session.messages.last().createdAt)
        assertEquals(session, f.disk.data.active)
        assertTrue(f.disk.data.pending.isEmpty())
    }

    @Test fun restoresConversationAndUnsentDraftAfterProcessRestart() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        model.send("Olá")
        advanceUntilIdle()
        model.editDraft("Ainda estou digitando")
        advanceUntilIdle()
        val restored = f.model()
        advanceUntilIdle()
        assertEquals(model.state.value.messages, restored.state.value.messages)
        assertEquals("Ainda estou digitando", restored.state.value.draft)
        assertEquals(2, f.calls)
        assertEquals(0, f.remote.attempts)
    }

    @Test fun failedExitKeepsLocalCopyAndRetryUsesTheSameSessionId() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.remote.failAfterWrite = true
        model.exit()
        advanceUntilIdle()
        val first = f.remote.saved.values.single()
        assertFalse(model.state.value.closed)
        assertTrue(model.state.value.queuedLocally)
        assertNotNull(model.state.value.exitError)
        assertEquals(first, f.disk.data.pending.single())
        f.remote.failAfterWrite = false
        model.exit()
        advanceUntilIdle()
        assertEquals(1, f.remote.saved.size)
        assertEquals(first, f.remote.saved.values.single())
        assertTrue(model.state.value.closed)
        assertTrue(f.disk.data.pending.isEmpty())
    }

    @Test fun pendingConversationContinuesAndUploadsTheUpdatedSnapshotOnNextExit() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.remote.fail = true
        model.exit()
        advanceUntilIdle()
        model.leaveLocally()
        assertTrue(model.state.value.closed)
        val previousId = f.disk.data.pending.single().id
        val next = f.model()
        advanceUntilIdle()
        assertEquals(previousId, f.disk.data.active!!.id)
        next.send("Continuando")
        advanceUntilIdle()
        f.remote.fail = false
        next.exit()
        advanceUntilIdle()
        assertEquals(1, f.remote.saved.size)
        assertEquals(3, f.remote.saved.values.single().messages.size)
        assertEquals(2, f.calls)
    }

    @Test fun exitCancelsInFlightAnswerAndKeepsTheUserMessage() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.stallAnswer = true
        model.send("Meu dia")
        runCurrent()
        model.exit()
        advanceUntilIdle()
        assertTrue(model.state.value.closed)
        assertEquals(listOf("assistant", "user"), f.remote.saved.values.single().messages.map { it.role })
    }

    @Test fun failedLocalWritePreventsApiAndUnsafeExit() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.disk.fail = true
        model.send("Meu dia")
        advanceUntilIdle()
        assertEquals(1, f.calls)
        assertNotNull(model.state.value.error)
        model.exit()
        advanceUntilIdle()
        assertFalse(model.state.value.closed)
        assertFalse(model.state.value.queuedLocally)
        assertEquals(0, f.remote.attempts)
        f.disk.fail = false
        model.exit()
        advanceUntilIdle()
        assertEquals("Meu dia", f.remote.saved.values.single().messages.last().content)
    }

    @Test fun apiFailureRetryDoesNotDuplicateUserMessage() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.apiFails = true
        model.send("Meu dia")
        advanceUntilIdle()
        assertEquals(2, f.disk.data.active!!.messages.size)
        f.apiFails = false
        model.retry()
        advanceUntilIdle()
        assertEquals(3, f.disk.data.active!!.messages.size)
        assertEquals(1, model.state.value.messages.count { it.role == "user" })
    }

    @Test fun duplicateSendIsBlockedWhileAnswerIsPending() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        model.send("Primeira")
        model.send("Duplicada")
        advanceUntilIdle()
        assertEquals(listOf("Primeira"), f.disk.data.active!!.messages.filter { it.role == "user" }.map { it.content })
    }

    @Test fun contextualQuestionUsesDatabaseUsageOnlyOnSelectedDays() = runTest(dispatcher) {
        val f = Fixture().apply { useContext = true }
        val model = f.model()
        advanceUntilIdle()
        assertEquals(1, f.usageCalls)
        assertTrue(f.requests.single().single().content.contains("com.roblox.client"))
        val general = Fixture().apply { useContext = false }
        general.model()
        advanceUntilIdle()
        assertEquals(0, general.usageCalls)
        assertFalse(general.requests.last().single().content.contains("com.roblox.client"))
    }

    @Test fun usageReadFailureAndTimeoutFallBackToGeneralDailyQuestion() = runTest(dispatcher) {
        for (stall in listOf(false, true)) {
            val f = Fixture().apply { useContext = true; usageFails = !stall; usageStalls = stall }
            val model = f.model()
            advanceUntilIdle()
            assertTrue(model.state.value.canSend)
            assertEquals(1, f.calls)
            assertFalse(f.requests.single().single().content.contains("com.roblox.client"))
        }
    }

    @Test fun historyLoadingDoesNotReplaceActiveConversationOrContactAi() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.remote.saved["old"] = ChatSession(id = "old", date = "2025-01-01", messages = listOf(ChatMessage(content = "Antiga")))
        val current = model.state.value.messages
        val page = model.historyPage()
        assertEquals("Antiga", page.sessions.single().messages.single().content)
        assertEquals(current, model.state.value.messages)
        assertEquals(1, f.calls)
        assertEquals(0, f.remote.attempts)
    }

    @Test fun crossingMidnightKeepsConversationUnderItsStartDate() = runTest(dispatcher) {
        val f = Fixture()
        val date = chatDate(f.time)
        val model = f.model()
        advanceUntilIdle()
        f.time += 86_400_000
        model.send("Outro dia")
        advanceUntilIdle()
        model.exit()
        advanceUntilIdle()
        assertEquals(date, f.remote.saved.values.single().date)
    }

    @Test fun switchingAccountsPreventsSendingConversationToAiOrDatabase() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        f.signedIn = false
        model.send("Meu dia")
        advanceUntilIdle()
        model.exit()
        advanceUntilIdle()
        assertEquals(1, f.calls)
        assertEquals(0, f.remote.attempts)
        assertFalse(model.state.value.closed)
    }

    @Test fun diskReadFailureDoesNotOverwriteLocalData() = runTest(dispatcher) {
        val f = Fixture().apply { disk.corrupt = true }
        val model = f.model()
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertEquals(0, f.calls)
        assertNull(f.disk.data.active)
    }

    @Test fun resumesAfterSuccessfulExitAndUpdatesTheSameCloudConversation() = runTest(dispatcher) {
        val f = Fixture()
        val model = f.model()
        advanceUntilIdle()
        model.send("Primeira mensagem")
        advanceUntilIdle()
        model.editDraft("Rascunho ao sair")
        advanceUntilIdle()
        model.exit()
        advanceUntilIdle()
        val id = f.remote.saved.keys.single()
        val reopened = f.model()
        advanceUntilIdle()
        assertEquals(model.state.value.messages, reopened.state.value.messages)
        assertEquals("Rascunho ao sair", reopened.state.value.draft)
        assertEquals(2, f.calls)
        reopened.send("Continuo de onde parei")
        advanceUntilIdle()
        assertTrue(f.requests.last().any { it.content == "Primeira mensagem" })
        reopened.exit()
        advanceUntilIdle()
        assertEquals(setOf(id), f.remote.saved.keys)
        assertEquals(5, f.remote.saved.getValue(id).messages.size)
    }

    @Test fun recoversLastSavedConversationWhenLocalSessionIsMissing() = runTest(dispatcher) {
        val f = Fixture()
        val saved = ChatSession("saved", chatDate(f.time), f.time, f.time,
            listOf(ChatMessage("00000000", "assistant", "Conversa anterior", f.time)))
        f.remote.saved[saved.id] = saved
        val model = f.model()
        advanceUntilIdle()
        assertEquals(saved.messages, model.state.value.messages)
        assertEquals(saved.id, f.disk.data.active!!.id)
        assertEquals(0, f.calls)
        assertTrue(model.state.value.canSend)
    }

    @Test fun historyPrefersNewPendingSnapshotOverOlderCloudVersion() = runTest(dispatcher) {
        val f = Fixture()
        val first = f.model()
        advanceUntilIdle()
        first.exit()
        advanceUntilIdle()
        val resumed = f.model()
        advanceUntilIdle()
        resumed.send("Mensagem ainda não sincronizada")
        advanceUntilIdle()
        f.remote.fail = true
        resumed.exit()
        advanceUntilIdle()
        f.remote.fail = false
        val page = resumed.historyPage()
        assertEquals(1, page.sessions.size)
        assertEquals(3, page.sessions.single().messages.size)
        assertEquals(1, f.remote.saved.values.single().messages.size)
    }
}
