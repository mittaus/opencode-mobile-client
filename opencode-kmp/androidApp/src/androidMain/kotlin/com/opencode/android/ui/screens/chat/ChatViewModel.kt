package com.opencode.android.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.shared.domain.model.*
import com.opencode.shared.data.local.PreferencesStorage
import com.opencode.shared.domain.repository.StatsRepository
import com.opencode.shared.domain.usecase.message.ExecuteCommandUseCase
import com.opencode.shared.domain.usecase.message.GetMessagesUseCase
import com.opencode.shared.domain.usecase.message.SendMessageAsyncUseCase
import com.opencode.shared.domain.usecase.session.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "OC-ChatVM"

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val inputText: String = "",
    val isRunning: Boolean = false,
    val activeAgent: String = "build",      // "build" | "plan"
    val selectedModel: String? = null,
    val selectedProvider: String? = null,
    val selectedImageBase64: String? = null,
    val selectedImageMimeType: String? = null,
    val pendingPermission: PermissionRequest? = null,
    val showBashDialog: Boolean = false,
    val bashInput: String = "",
    val activeTab: ChatTab = ChatTab.CHAT,
    val error: String? = null,
    /** Accumulated streaming text per messageId while the assistant is typing */
    val streamingContent: Map<String, String> = emptyMap(),
    val sessionDirectory: String = "",
)

enum class ChatTab { CHAT, TODO }

class ChatViewModel(
    private val sessionId: String,
    private val getMessages: GetMessagesUseCase,
    private val sendMessageAsync: SendMessageAsyncUseCase,
    private val executeCommand: ExecuteCommandUseCase,
    private val abortSession: AbortSessionUseCase,
    private val revertMessage: RevertMessageUseCase,
    private val respondPermission: RespondPermissionUseCase,
    private val observeEvents: ObserveEventsUseCase,
    private val getSessions: GetSessionsUseCase,
    private val prefs: PreferencesStorage,
    private val statsRepo: StatsRepository,
) : ViewModel() {

    private val prefKeyModel    = "session_model_$sessionId"
    private val prefKeyProvider = "session_provider_$sessionId"

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        Log.d(TAG, "init sessionId=$sessionId")
        loadSessionModel()
        loadMessages()
        observeSseEvents()
    }

    private fun loadSessionModel() {
        viewModelScope.launch {
            // 1. Try to get the model that the server has configured for this session
            getSessions().onSuccess { sessions ->
                val session = sessions.find { it.id == sessionId }
                if (session != null && session.modelId.isNotBlank()) {
                    _state.update {
                        it.copy(selectedModel = session.modelId, selectedProvider = session.providerId)
                    }
                    Log.d(TAG, "loadSessionModel ✓ from server: model=${session.modelId} provider=${session.providerId}")
                    return@launch
                }
            }
            // 2. Fall back to locally saved preference (e.g. user had previously chosen a model)
            val model    = prefs.getString(prefKeyModel)
            val provider = prefs.getString(prefKeyProvider)
            if (!model.isNullOrBlank()) {
                _state.update { it.copy(selectedModel = model, selectedProvider = provider) }
                Log.d(TAG, "loadSessionModel ✓ from prefs: model=$model provider=$provider")
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            getMessages(sessionId).onSuccess { msgs ->
                Log.d(TAG, "loadMessages ✓ count=${msgs.size}")
                _state.update { it.copy(messages = msgs) }
            }.onFailure { e ->
                Log.e(TAG, "loadMessages ✗ ${e.message}")
            }
        }
    }

    private fun observeSseEvents() {
        viewModelScope.launch {
            Log.d(TAG, "observeSseEvents started")
            observeEvents().collect { event ->
                Log.d(TAG, "SSE event received: ${event::class.simpleName}")
                when (event) {
                    is ServerEvent.MessageUpdated -> {
                        Log.d(TAG, "MessageUpdated event.sessionId=${event.sessionId} this.sessionId=$sessionId match=${event.sessionId == sessionId}")
                        if (event.sessionId == sessionId) {
                            _state.update { s ->
                                val idx = s.messages.indexOfFirst { it.id == event.message.id }
                                val updated = when {
                                    idx >= 0 && event.message.role == MessageRole.USER && event.message.parts.isEmpty() -> {
                                        // Incoming user message has no parts — keep existing content, don't overwrite
                                        s.messages
                                    }
                                    idx >= 0 -> {
                                        s.messages.toMutableList().also { it[idx] = event.message }
                                    }
                                    event.message.role == MessageRole.USER && event.message.parts.isEmpty() -> {
                                        // Real user message arrived with no parts — update the optimistic message's ID
                                        // so it can be matched by future events, preserving visible text
                                        val optimisticIdx = s.messages.indexOfFirst { it.id.startsWith("optimistic-") }
                                        if (optimisticIdx >= 0) {
                                            s.messages.toMutableList().also {
                                                it[optimisticIdx] = it[optimisticIdx].copy(id = event.message.id)
                                            }
                                        } else {
                                            s.messages
                                        }
                                    }
                                    else -> {
                                        s.messages + event.message
                                    }
                                }
                                // track token usage
                                event.message.tokens?.let { tk ->
                                    viewModelScope.launch {
                                        statsRepo.recordMessageUsage(
                                            sessionId,
                                            s.selectedModel ?: "",
                                            s.selectedProvider ?: "",
                                            tk.inputTokens,
                                            tk.outputTokens,
                                        )
                                    }
                                }
                                // track tool calls
                                event.message.parts.filterIsInstance<MessagePart.ToolCall>()
                                    .forEach { tc ->
                                        viewModelScope.launch { statsRepo.recordToolCall(tc.toolName) }
                                    }
                                s.copy(messages = updated, isRunning = false)
                            }
                        }
                    }
                    is ServerEvent.PermissionRequested -> {
                        if (event.request.sessionId == sessionId)
                            _state.update { it.copy(pendingPermission = event.request) }
                    }
                    is ServerEvent.TodoUpdated -> {
                        if (event.sessionId == sessionId)
                            _state.update { it.copy(todos = event.todos) }
                    }
                    is ServerEvent.MessagePartDelta -> {
                        if (event.sessionId == sessionId && event.field == "text") {
                            _state.update { s ->
                                val current = s.streamingContent[event.messageId] ?: ""
                                s.copy(streamingContent = s.streamingContent + (event.messageId to current + event.delta))
                            }
                        }
                    }
                    is ServerEvent.SessionBusy ->
                        if (event.sessionId == sessionId)
                            _state.update { it.copy(isRunning = true) }
                    is ServerEvent.SessionIdle ->
                        if (event.sessionId == sessionId) {
                            _state.update { it.copy(isRunning = false, streamingContent = emptyMap()) }
                            loadMessages()
                        }
                    is ServerEvent.SessionAborted ->
                        _state.update { it.copy(isRunning = false, streamingContent = emptyMap()) }
                    is ServerEvent.SessionUpdated -> {
                        if (event.session.id == sessionId) {
                            _state.update {
                                it.copy(
                                    selectedModel = event.session.modelId.takeIf { m -> m.isNotBlank() } ?: it.selectedModel,
                                    selectedProvider = event.session.providerId.takeIf { p -> p.isNotBlank() } ?: it.selectedProvider,
                                    sessionDirectory = event.session.directory.takeIf { d -> d.isNotBlank() } ?: it.sessionDirectory,
                                )
                            }
                            Log.d(TAG, "SessionUpdated model=${event.session.modelId} dir=${event.session.directory}")
                        }
                    }
                    is ServerEvent.Connected -> Log.d(TAG, "SSE Connected: ${event.version}")
                    is ServerEvent.Error -> {
                        Log.e(TAG, "SSE Error: ${event.message}")
                        _state.update { it.copy(error = event.message, isRunning = false) }
                    }
                    is ServerEvent.Unknown ->
                        if (event.type !in setOf(
                                "server.heartbeat", "session.idle", "session.diff",
                                "session.status", "message.part.updated",
                            ))
                            Log.w(TAG, "SSE Unknown type=${event.type} raw=${event.raw.take(100)}")
                    else -> Unit
                }
            }
        }
    }

    fun onInputChange(text: String) {
        // Log.d(TAG, "onInputChange: '$text'")
        _state.update { it.copy(inputText = text) }
    }
    fun onTabChange(tab: ChatTab) = _state.update { it.copy(activeTab = tab) }
    fun onAgentChange(agent: String) {
        Log.d(TAG, "onAgentChange: $agent")
        _state.update { it.copy(activeAgent = agent) }
    }

    fun sendMessage() {
        val st = _state.value
        Log.d(TAG, "sendMessage BUTTON CLICKED! isRunning=${st.isRunning} inputText='${st.inputText}' model='${st.selectedModel}'")
        val text = st.inputText.trim()
        if (text.isBlank()) return
        Log.d(TAG, "sendMessage proceed text=\"${text.take(80)}\" session=$sessionId model=${st.selectedModel} agent=${st.activeAgent}")
        val optimisticUserMsg = Message(
            id = "optimistic-${System.currentTimeMillis()}",
            sessionId = sessionId,
            role = MessageRole.USER,
            parts = buildList {
                if (st.selectedImageBase64 != null && st.selectedImageMimeType != null) {
                    add(MessagePart.Image(st.selectedImageBase64, st.selectedImageMimeType))
                }
                add(MessagePart.Text(text))
            },
            createdAt = System.currentTimeMillis(),
        )
        _state.update { 
            it.copy(
                inputText = "", 
                isRunning = true, 
                messages = it.messages + optimisticUserMsg,
                selectedImageBase64 = null,
                selectedImageMimeType = null
            ) 
        }
        val modelId = st.selectedModel
        val providerId = st.selectedProvider
        val agent = st.activeAgent
        val imageBase64 = st.selectedImageBase64
        val imageMimeType = st.selectedImageMimeType
        viewModelScope.launch {
            sendMessageAsync(
                sessionId = sessionId,
                text = text,
                modelId = modelId,
                providerId = providerId,
                agent = agent,
                imageBase64 = imageBase64,
                imageMimeType = imageMimeType,
            ).onFailure { e ->
                Log.e(TAG, "sendMessage ✗ ${e.message}")
                _state.update { it.copy(error = e.message, isRunning = false) }
            }
            // Response arrives via SSE — no need to wait here
        }
    }

    fun abort() {
        viewModelScope.launch {
            abortSession(sessionId).onSuccess {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    fun undoLast() {
        val lastAssistant = _state.value.messages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?: return
        viewModelScope.launch {
            revertMessage(sessionId, lastAssistant.id).onSuccess {
                _state.update { s ->
                    val idx = s.messages.indexOfFirst { it.id == lastAssistant.id }
                    s.copy(messages = if (idx >= 0) s.messages.take(idx) else s.messages)
                }
            }
        }
    }

    fun allowPermission(remember: Boolean = false) {
        val perm = _state.value.pendingPermission ?: return
        viewModelScope.launch {
            respondPermission(
                sessionId, perm.id,
                if (remember) PermissionResponse.ALLOW_ALWAYS else PermissionResponse.ALLOW,
            )
            _state.update { it.copy(pendingPermission = null) }
        }
    }

    fun denyPermission() {
        val perm = _state.value.pendingPermission ?: return
        viewModelScope.launch {
            respondPermission(sessionId, perm.id, PermissionResponse.DENY)
            _state.update { it.copy(pendingPermission = null, isRunning = false) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun openBashDialog() = _state.update { it.copy(showBashDialog = true, bashInput = "") }
    fun closeBashDialog() = _state.update { it.copy(showBashDialog = false, bashInput = "") }
    fun onBashInputChange(text: String) = _state.update { it.copy(bashInput = text) }

    fun onImageSelected(base64: String, mimeType: String) {
        _state.update { it.copy(selectedImageBase64 = base64, selectedImageMimeType = mimeType) }
    }

    fun clearSelectedImage() {
        _state.update { it.copy(selectedImageBase64 = null, selectedImageMimeType = null) }
    }

    fun executeBash() {
        val st = _state.value
        val cmd = st.bashInput.trim()
        if (cmd.isBlank()) return
        Log.d(TAG, "executeBash cmd=\"$cmd\"")
        // /command endpoint is for OpenCode slash-commands (/review, /init), not arbitrary bash.
        // Send the bash command as a regular prompt — OpenCode will use its bash tool to run it.
        _state.update { it.copy(showBashDialog = false, bashInput = "", inputText = cmd) }
        sendMessage()
    }

    fun setModel(modelId: String, providerId: String) {
        _state.update { it.copy(selectedModel = modelId, selectedProvider = providerId) }
        viewModelScope.launch {
            prefs.putString(prefKeyModel, modelId)
            prefs.putString(prefKeyProvider, providerId)
            Log.d(TAG, "setModel saved model=$modelId provider=$providerId")
        }
    }
}
