package com.fabio.eagleeyes.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val draft: String = "",
    val loading: Boolean = true, val error: String? = null,
    val exiting: Boolean = false, val saving: Boolean = false,
    val exitError: String? = null, val queuedLocally: Boolean = false, val closed: Boolean = false,
    val notice: String? = null
) {
    val canSend get() = messages.lastOrNull()?.role == "assistant" && !loading && !exiting && error == null
}

class ChatViewModel(
    private val repository: ChatResponder,
    private val history: ChatHistoryStore,
    private val local: ChatLocalStore,
    private val usage: ChatUsageStore,
    private val uid: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val contextualDay: (String, String) -> Boolean = DailyQuestionPolicy::useUsage,
    private val currentAccount: () -> Boolean = { true }
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatState())
    val state = mutableState.asStateFlow()
    private val diskLock = Mutex()
    private var chats = LocalChats()
    private var initialized = false
    private var generation: Job? = null
    private var exitSnapshotQueued = false

    init { respond() }

    private fun checkAccount() = check(currentAccount()) { "Entre novamente na conta do jovem." }

    private suspend fun initialize() {
        if (initialized) return
        checkAccount()
        chats = local.read()
        if (chats.active == null) {
            val pending = chats.pending.maxByOrNull { maxOf(it.startedAt, it.endedAt) }
            val saved = pending ?: try {
                withTimeoutOrNull(8_000) { history.load().sessions.maxByOrNull { maxOf(it.startedAt, it.endedAt) } }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            val time = now()
            chats = chats.copy(active = saved?.copy(endedAt = 0)
                ?: ChatSession(UUID.randomUUID().toString(), chatDate(time), time))
        }
        val active = requireNotNull(chats.active)
        mutableState.value = state.value.copy(messages = active.messages, draft = active.draft,
            notice = if (chats.pending.isNotEmpty()) "Há conversas guardadas neste aparelho. Tentaremos enviá-las ao sair." else null)
        initialized = true
    }

    fun editDraft(text: String) {
        if (!initialized || state.value.exiting || text == state.value.draft) return
        mutableState.value = state.value.copy(draft = text.take(4000))
        viewModelScope.launch {
            try { persistActive() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = state.value.copy(error = "Não foi possível salvar no aparelho. Tente novamente antes de sair.") }
        }
    }

    private suspend fun persistActive() = diskLock.withLock {
        val active = chats.active ?: return@withLock
        val next = chats.copy(active = active.copy(messages = state.value.messages, draft = state.value.draft))
        local.write(next)
        chats = next
    }

    fun send(text: String) {
        val content = text.trim()
        if (!state.value.canSend || content.isBlank() || content.length > 4000) return
        mutableState.value = state.value.copy(messages = state.value.messages + message("user", content), draft = "")
        respond()
    }

    fun retry() { if (!state.value.loading && !state.value.exiting) respond() }

    private fun message(role: String, text: String) = ChatMessage(
        id = state.value.messages.size.toString().padStart(8, '0'), role = role, content = text,
        createdAt = maxOf(now(), (state.value.messages.lastOrNull()?.createdAt ?: 0) + 1)
    )

    private fun respond() {
        mutableState.value = state.value.copy(loading = true, error = null)
        generation = viewModelScope.launch {
            try {
                initialize()
                checkAccount()
                persistActive()
                val last = state.value.messages.lastOrNull()
                if (last == null) {
                    val today = chatDate(now())
                    val cached = chats.daily?.takeIf { it.date == today }
                    val text = cached?.text ?: run {
                        val summary = if (contextualDay(today, uid)) {
                            try { withTimeoutOrNull(8_000) { usage.summary(today) } }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        } else null
                        checkAccount()
                        repository.reply(listOf(Message("user", DailyQuestionPolicy.prompt(summary))))
                    }
                    checkAccount()
                    mutableState.value = state.value.copy(messages = listOf(message("assistant", text)))
                    diskLock.withLock { chats = chats.copy(daily = DailyOpening(today, text)) }
                    persistActive()
                } else if (last.role == "user") {
                    val text = repository.reply(state.value.messages.map { Message(it.role, it.content) })
                    checkAccount()
                    mutableState.value = state.value.copy(messages = state.value.messages + message("assistant", text))
                    persistActive()
                }
                mutableState.value = state.value.copy(loading = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.value = state.value.copy(loading = false, error = error.message ?: "Não foi possível continuar. Tente novamente.")
            }
        }
    }

    fun exit() {
        if (state.value.saving || state.value.closed) return
        mutableState.value = state.value.copy(exiting = true, saving = true, loading = false, exitError = null)
        val job = generation
        job?.cancel()
        viewModelScope.launch {
            try {
                job?.join()
                checkAccount()
                // Mantenha a sessão para continuar ao voltar. A cópia pendente é um snapshot.
                diskLock.withLock {
                    val active = chats.active
                    if (active != null && !exitSnapshotQueued) {
                        val closed = active.copy(messages = state.value.messages, draft = state.value.draft,
                            endedAt = maxOf(now(), state.value.messages.lastOrNull()?.createdAt ?: active.startedAt))
                        val next = chats.copy(active = closed,
                            pending = if (closed.messages.isEmpty()) chats.pending
                            else chats.pending.filterNot { it.id == closed.id } + closed)
                        local.write(next)
                        chats = next
                        exitSnapshotQueued = true
                    }
                }
                mutableState.value = state.value.copy(queuedLocally = true)
                while (true) {
                    val pending = diskLock.withLock { chats.pending.firstOrNull() } ?: break
                    withTimeout(20_000) { history.save(pending) }
                    diskLock.withLock {
                        val next = chats.copy(pending = chats.pending.filterNot { it.id == pending.id })
                        local.write(next)
                        chats = next
                    }
                }
                mutableState.value = state.value.copy(saving = false, closed = true)
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
                exitFailed()
            } catch (_: Exception) { exitFailed() }
        }
    }

    private fun exitFailed() {
        mutableState.value = state.value.copy(saving = false, exitError = if (state.value.queuedLocally)
            "A conversa está salva neste aparelho, mas o envio ao banco não foi confirmado. Você pode tentar novamente ou sair com a cópia local."
        else "Não foi possível guardar a conversa neste aparelho. Tente novamente para não perder as mensagens.")
    }

    fun leaveLocally() {
        if (state.value.queuedLocally && !state.value.saving) mutableState.value = state.value.copy(closed = true)
    }

    suspend fun historyPage(beforeDate: String? = null): ChatHistoryPage {
        checkAccount()
        val pending = diskLock.withLock { chats.pending }
        return try {
            val page = withTimeout(20_000) { history.load(beforeDate) }
            page.copy(sessions = (pending.filter { beforeDate == null || it.date < beforeDate } + page.sessions)
                .distinctBy { it.id }.sortedByDescending { it.startedAt })
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            if (pending.isEmpty()) throw ChatException("Não foi possível carregar o histórico. Confira sua conexão.")
            throw ChatException("O banco não respondeu. As conversas pendentes continuam salvas neste aparelho.")
        }
    }

    suspend fun localPending(): List<ChatSession> = diskLock.withLock { chats.pending }
}
