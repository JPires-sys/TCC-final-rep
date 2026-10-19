package com.fabio.eagleeyes.chat

import com.fabio.eagleeyes.R

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.fabio.eagleeyes.global.FirebaseConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {
    private lateinit var model: ChatViewModel
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: MaterialButton
    private lateinit var scroll: ScrollView
    private var renderedCount = 0
    private var accountUid = ""
    private var exitDialog: AlertDialog? = null
    private var historyDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        accountUid = FirebaseConfig.getAuth().currentUser?.uid.orEmpty()
        if (accountUid.isBlank()) { finish(); return }
        val uid = accountUid
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                ChatRepository(), ChatHistoryRepository(uid),
                FileChatLocalStore(File(noBackupFilesDir, "chatbox"), uid),
                ChatUsageRepository(uid), uid,
                currentAccount = { FirebaseConfig.getAuth().currentUser?.uid == uid }
            ) as T
        })[ChatViewModel::class.java]
        findViewById<View>(R.id.btnChatVoltar).setOnClickListener { model.exit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { model.exit() }
        })
        findViewById<View>(R.id.btnChatHistory).setOnClickListener { showHistory() }
        chatContainer = findViewById(R.id.chatContainer)
        input = findViewById(R.id.inputMensagem)
        send = findViewById(R.id.btnEnviar)
        scroll = findViewById(R.id.scrollView)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.welcomeTitle), true)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        input.setText(model.state.value.draft)
        input.doAfterTextChanged {
            model.editDraft(it?.toString().orEmpty())
            updateSend()
        }
        send.setOnClickListener { sendMessage() }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
        findViewById<View>(R.id.btnRetry).setOnClickListener { model.retry() }
        findViewById<View>(R.id.btnAbout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chatbox_about).setMessage(R.string.chatbox_about_body)
                .setPositiveButton(R.string.chatbox_close, null).show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
    }

    private fun sendMessage() {
        if (!model.state.value.canSend || input.text.isNullOrBlank()) return
        model.send(input.text.toString())
        if (model.state.value.draft.isEmpty()) input.text.clear()
    }

    private fun updateSend() {
        send.isEnabled = model.state.value.canSend && !input.text.isNullOrBlank()
        send.alpha = if (send.isEnabled) 1f else 0.4f
    }

    private fun render(state: ChatState) {
        if (state.closed) { finish(); return }
        if (input.text.toString() != state.draft) {
            input.setText(state.draft)
            input.setSelection(input.text.length)
        }
        input.isEnabled = !state.exiting && state.messages.isNotEmpty()
        findViewById<View>(R.id.btnChatVoltar).isEnabled = !state.saving
        findViewById<View>(R.id.btnChatHistory).isEnabled = !state.exiting
        findViewById<TextView>(R.id.chatNotice).apply {
            text = state.notice
            isVisible = !state.notice.isNullOrBlank()
        }
        val addedMessages = state.messages.size > renderedCount
        state.messages.drop(renderedCount).forEach(::addMessage)
        renderedCount = state.messages.size
        findViewById<View>(R.id.loadingContainer).isVisible = state.loading || state.saving
        findViewById<TextView>(R.id.loadingText).setText(
            if (state.saving) R.string.chatbox_saving else if (state.messages.isEmpty()) R.string.chatbox_loading_initial else R.string.chatbox_loading_reply
        )
        findViewById<View>(R.id.errorContainer).isVisible = state.error != null
        findViewById<TextView>(R.id.errorText).text = state.error
        findViewById<View>(R.id.btnRetry).isEnabled = !state.exiting
        if (state.exitError != null && exitDialog?.isShowing != true) {
            val builder = MaterialAlertDialogBuilder(this).setTitle("Salvar conversa")
                .setMessage(state.exitError).setCancelable(false)
                .setPositiveButton("Tentar novamente") { _, _ -> model.exit() }
            if (state.queuedLocally) builder.setNegativeButton("Sair com cópia local") { _, _ -> model.leaveLocally() }
            exitDialog = builder.show()
        }
        updateSend()
        // Keep the welcome card visible for the opening; follow subsequent replies.
        if ((addedMessages || state.error != null) && state.messages.size > 1) {
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onStart() {
        super.onStart()
        if (accountUid.isNotBlank() && FirebaseConfig.getAuth().currentUser?.uid != accountUid) finish()
    }

    private fun showHistory(beforeDate: String? = null) {
        historyDialog?.dismiss()
        val loading = MaterialAlertDialogBuilder(this).setTitle("Histórico por data")
            .setMessage("Carregando conversas…").setNegativeButton("Fechar", null).show()
        historyDialog = loading
        lifecycleScope.launch {
            try {
                val page = model.historyPage(beforeDate)
                val pendingIds = model.localPending().map { it.id }.toSet()
                if (loading.isShowing && !isFinishing) {
                    loading.dismiss()
                    showHistoryList(page, pendingIds)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                val pending = model.localPending()
                if (loading.isShowing && !isFinishing) {
                    loading.dismiss()
                    if (pending.isNotEmpty()) showHistoryList(ChatHistoryPage(pending, null), pending.map { it.id }.toSet(), true)
                    else historyDialog = MaterialAlertDialogBuilder(this@ChatActivity).setTitle("Histórico por data")
                        .setMessage("Não foi possível carregar as conversas do banco. Confira sua conexão e tente novamente.")
                        .setPositiveButton("Tentar novamente") { _, _ -> showHistory(beforeDate) }
                        .setNegativeButton("Fechar", null).show()
                }
            }
        }
    }

    private fun showHistoryList(page: ChatHistoryPage, pendingIds: Set<String>, offline: Boolean = false) {
        val labels = page.sessions.map { session ->
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startedAt))
            val preview = session.messages.firstOrNull { it.role == "user" }?.content ?: "Mensagem do dia"
            "${displayDate(session.date)} · $time${if (session.id in pendingIds) " · Pendente" else ""}\n${preview.take(55)}"
        }.toMutableList()
        if (page.olderThan != null) labels.add("Carregar datas anteriores")
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (offline) "Conversas locais — banco indisponível" else "Histórico por data")
            .setNegativeButton("Fechar", null)
        if (labels.isEmpty()) builder.setMessage("Nenhuma conversa salva ainda. Ao sair do chat, a conversa será enviada ao banco.")
        else builder.setItems(labels.toTypedArray()) { _, index ->
            if (index == page.sessions.size) showHistory(page.olderThan)
            else showConversation(page.sessions[index], page.sessions[index].id in pendingIds)
        }
        historyDialog = builder.show()
    }

    private fun showConversation(session: ChatSession, pending: Boolean) {
        val content = TextView(this).apply {
            setPadding(dp(24), dp(12), dp(24), dp(24))
            textSize = 16f
            setTextIsSelectable(true)
            text = (if (pending) "Salva neste aparelho; envio ao banco pendente.\n\n" else "") +
                session.messages.joinToString("\n\n") { message ->
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
                    "${if (message.role == "user") "VOCÊ" else "CHATBOX"} · $time\n${message.content}"
                }
        }
        historyDialog = MaterialAlertDialogBuilder(this).setTitle(displayDate(session.date))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Fechar", null).setNeutralButton("Voltar ao histórico") { _, _ -> showHistory() }.show()
    }

    private fun displayDate(date: String): String = date.split('-').let {
        if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else date
    }

    override fun onDestroy() {
        historyDialog?.dismiss()
        exitDialog?.dismiss()
        super.onDestroy()
    }

    private fun addMessage(message: ChatMessage) {
        val isUser = message.role == "user"
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
            setPadding(if (isUser) dp(36) else 0, 0, if (isUser) 0 else dp(24), 0)
            gravity = if (isUser) Gravity.END else Gravity.START
        }
        group.addView(TextView(this).apply {
            text = if (isUser) "VOCÊ" else getString(R.string.chatbox_ai_label)
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.chat_muted))
            setPadding(dp(4), 0, dp(4), dp(7))
        })
        group.addView(TextView(this).apply {
            text = message.content
            textSize = 16f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(getColor(if (isUser) R.color.chat_on_primary else R.color.chat_text))
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = GradientDrawable().apply {
                setColor(getColor(if (isUser) R.color.chat_primary else R.color.chat_surface))
                cornerRadius = dp(20).toFloat()
                if (!isUser) setStroke(dp(1), getColor(R.color.chat_border))
            }
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        chatContainer.addView(group)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
