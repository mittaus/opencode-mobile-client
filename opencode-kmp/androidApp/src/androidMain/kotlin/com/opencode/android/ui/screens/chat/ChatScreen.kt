package com.opencode.android.ui.screens.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.model.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChatScreen(
    sessionId: String,
    projectPath: String = "",
    returnedModelId: String? = null,
    returnedProviderId: String? = null,
    onModelConsumed: () -> Unit = {},
    onOpenFiles: () -> Unit,
    onOpenStats: () -> Unit,
    onChangeModel: (currentModelId: String) -> Unit,
    vm: ChatViewModel = koinViewModel(parameters = { parametersOf(sessionId) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(it) ?: "image/jpeg"
            contentResolver.openInputStream(it)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                vm.onImageSelected(base64, mimeType)
            }
        }
    }

    // Apply model returned from ModelsScreen
    LaunchedEffect(returnedModelId) {
        if (!returnedModelId.isNullOrBlank()) {
            vm.setModel(returnedModelId, returnedProviderId ?: "")
            onModelConsumed()
        }
    }

    // Show error as a dismissible banner
    state.error?.let { errorMsg ->
        LaunchedEffect(errorMsg) {
            kotlinx.coroutines.delay(5000)
            vm.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            // ── Header ──
            ChatHeader(
                state = state,
                projectPath = projectPath,
                onChangeModel = { onChangeModel(state.selectedModel ?: "") },
                onOpenFiles = onOpenFiles,
                onOpenStats = onOpenStats,
                onAgentChange = vm::onAgentChange,
            )
            // ── Tabs ──
            ChatTabs(
                active = state.activeTab,
                todoCount = state.todos.count { it.status != TodoStatus.COMPLETED },
                onTabChange = vm::onTabChange,
            )
            // ── Content ──
            Box(Modifier.weight(1f)) {
                when (state.activeTab) {
                    ChatTab.CHAT -> MessageList(
                        messages = state.messages,
                        streamingContent = state.streamingContent,
                        isRunning = state.isRunning,
                    )
                    ChatTab.TODO -> TodoList(todos = state.todos)
                }
            }
            // ── Input ──
            ChatInput(
                text = state.inputText,
                isRunning = state.isRunning,
                selectedImageMimeType = state.selectedImageMimeType,
                onTextChange = vm::onInputChange,
                onSend = vm::sendMessage,
                onStop = vm::abort,
                onUndo = vm::undoLast,
                onFileClick = { imagePickerLauncher.launch("image/*") },
                onClearImage = vm::clearSelectedImage
                // onBash = vm::openBashDialog,  // Bash deshabilitado temporalmente
            )
        }

        // ── Error Banner ──
        state.error?.let { errorMsg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFFB00020), RoundedCornerShape(8.dp))
                    .clickable { vm.clearError() }
                    .padding(12.dp)
            ) {
                Text(
                    errorMsg,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Bash Dialog ──
        if (state.showBashDialog) {
            BashDialog(
                input = state.bashInput,
                onInputChange = vm::onBashInputChange,
                onExecute = vm::executeBash,
                onDismiss = vm::closeBashDialog,
            )
        }

        // ── Permission Sheet ──
        state.pendingPermission?.let { perm ->
            PermissionSheet(
                request = perm,
                onAllow = { vm.allowPermission(remember = false) },
                onAllowAlways = { vm.allowPermission(remember = true) },
                onDeny = vm::denyPermission,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    state: ChatUiState,
    projectPath: String,
    onChangeModel: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenStats: () -> Unit,
    onAgentChange: (String) -> Unit,
) {
    Surface(color = SurfaceDark, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp)
                        .background(Green, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("ses_${state.messages.firstOrNull()?.sessionId?.take(6) ?: "…"}",
                        style = MaterialTheme.typography.labelMedium, color = Green)
                    val displayPath = state.sessionDirectory.ifBlank { projectPath }
                    if (displayPath.isNotEmpty()) {
                        Text(
                            displayPath.substringAfterLast('\\').substringAfterLast('/').ifEmpty { displayPath },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextThird,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OcIconButton(painterResource(com.opencode.android.R.drawable.ic_git), onOpenFiles)
                Spacer(Modifier.width(6.dp))
                OcIconButton("📊", onOpenStats)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Model badge
                Box(
                    Modifier
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .clickable(onClick = onChangeModel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(5.dp).background(Orange, RoundedCornerShape(50)))
                        Text(
                            state.selectedModel ?: "Seleccionar modelo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecond,
                            maxLines = 1,
                        )
                        Text("▼", style = MaterialTheme.typography.labelSmall, color = TextThird)
                    }
                }
                // Agent toggle
                Row(
                    Modifier
                        .background(CardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                ) {
                    listOf("build" to "⚙ Build", "plan" to "📋 Plan").forEach { (id, label) ->
                        val isActive = state.activeAgent == id
                        Box(
                            Modifier
                                .background(
                                    if (isActive && id == "build") Green.copy(alpha = 0.15f)
                                    else if (isActive) Purple.copy(alpha = 0.15f)
                                    else CardDark,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onAgentChange(id) }
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive && id == "build") Green
                                        else if (isActive) Purple
                                        else TextThird,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTabs(active: ChatTab, todoCount: Int, onTabChange: (ChatTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .border(BorderStroke(1.dp, BorderDark), shape = RoundedCornerShape(0.dp)),
    ) {
        ChatTab.entries.forEach { tab ->
            val isActive = tab == active
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTabChange(tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (tab == ChatTab.CHAT) "💬 Chat" else "✅ Todo",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) Green else TextThird,
                    )
                    if (tab == ChatTab.TODO && todoCount > 0) {
                        Box(
                            Modifier
                                .background(GreenDim, RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("$todoCount", style = MaterialTheme.typography.labelSmall, color = Green)
                        }
                    }
                }
                if (isActive) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(32.dp).height(2.dp).background(Green, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    streamingContent: Map<String, String>,
    isRunning: Boolean,
) {
    val listState = rememberLazyListState()
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, streamingContent.isNotEmpty()) {
        listState.scrollToItem(0)
    }
    // Build display list newest-first so reverseLayout shows latest at bottom
    val extraItems = buildList {
        streamingContent.entries
            .filter { (id, _) -> messages.none { it.id == id } }
            .forEach { (_, text) -> add(null to text) }
        if (isRunning && streamingContent.isEmpty()) add(null to null)
    }
    val reversedMessages = messages.asReversed()
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Extra items first (they appear at bottom in reverseLayout)
        extraItems.forEach { (_, text) ->
            item {
                if (text != null) StreamingBubble(text)
                else Row(Modifier.padding(4.dp)) { TypingIndicator() }
            }
        }
        items(reversedMessages, key = { it.id }) { msg ->
            val streaming = streamingContent[msg.id]
            MessageRow(
                message = msg,
                streamingOverlay = streaming,
                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("opencode", style = MaterialTheme.typography.labelSmall, color = TextThird,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(
            Modifier
                .background(CardDark, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp)
                .widthIn(max = 300.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextThird)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: Message,
    streamingOverlay: String? = null,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER

    // Skip rendering messages with no content and no streaming overlay
    val effectiveParts = message.parts.ifEmpty {
        if (streamingOverlay != null) listOf(MessagePart.Text(streamingOverlay)) else emptyList()
    }
    if (effectiveParts.isEmpty()) return

    val clipboard = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    // Full text content of the message for copying
    val fullText = remember(effectiveParts) {
        effectiveParts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
    }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            if (!isUser && showCopied) {
                Text("✓ Copiado", style = MaterialTheme.typography.labelSmall, color = Green)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (isUser) "tú" else "opencode",
                style = MaterialTheme.typography.labelSmall,
                color = TextThird,
            )
            if (isUser && showCopied) {
                Spacer(Modifier.width(6.dp))
                Text("✓ Copiado", style = MaterialTheme.typography.labelSmall, color = Green)
            }
        }
        val isStreaming = message.parts.isEmpty() && streamingOverlay != null
        val partsToShow = effectiveParts
        partsToShow.forEach { part ->
            when (part) {
                is MessagePart.Text -> {
                    val bubbleShape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                    Box(
                        Modifier
                            .background(if (isUser) GreenDim else CardDark, bubbleShape)
                            .border(1.dp, if (isUser) GreenMid else BorderDark, bubbleShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (fullText.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(fullText))
                                        showCopied = true
                                    }
                                },
                            )
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                            .widthIn(max = 300.dp),
                    ) {
                        Text(
                            part.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isStreaming) TextThird else TextPrimary,
                        )
                    }
                }
                is MessagePart.ToolCall -> {
                    Spacer(Modifier.height(4.dp))
                    val (icon, color) = when {
                        part.toolName.contains("edit") -> "✏️" to Yellow
                        part.toolName.contains("bash") || part.toolName.contains("shell") -> "💻" to Green
                        else -> "📖" to Blue
                    }
                    ToolCallBubble(
                        icon = icon,
                        label = "${part.toolName} · ${part.input.values.firstOrNull()?.take(40) ?: ""}",
                        accent = color,
                    )
                }
                is MessagePart.ToolResult -> {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "✓ ${part.toolName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (part.isError) Red else TextThird,
                    )
                }
                is MessagePart.StepStart -> {
                    Text(part.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = Purple,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
                is MessagePart.Image -> {
                    Spacer(Modifier.height(4.dp))
                    ToolCallBubble(icon = "🖼", label = "Imagen adjunta", accent = Green)
                }
            }
        }
    }
}

@Composable
private fun TodoList(todos: List<TodoItem>) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { SectionLabel("Tareas de la sesión actual") }
        items(todos) { todo ->
            TodoRow(todo)
        }
    }
}

@Composable
private fun TodoRow(todo: TodoItem) {
    val isActive = todo.status == TodoStatus.IN_PROGRESS
    val isDone = todo.status == TodoStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) GreenDim else CardDark,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (isActive) Green.copy(alpha = 0.25f) else BorderDark,
                RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(
                    if (isDone) Green else if (isActive) GreenDim else CardDark,
                    RoundedCornerShape(4.dp),
                )
                .border(1.dp, if (isDone || isActive) Green else BorderDark, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isDone) Text("✓", style = MaterialTheme.typography.labelSmall, color = BgDark)
            else if (isActive) Text("…", style = MaterialTheme.typography.labelSmall, color = Green)
        }
        Column {
            Text(
                todo.content,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isDone -> TextThird
                    isActive -> Green
                    else -> TextPrimary
                },
                textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
        }
    }
}

@Composable
private fun ChatInput(
    text: String,
    isRunning: Boolean,
    selectedImageMimeType: String? = null,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
    onFileClick: () -> Unit,
    onClearImage: () -> Unit,
    // onBash: () -> Unit,  // Funcionalidad Bash deshabilitada temporalmente
) {
    Surface(color = SurfaceDark, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // OcChip("⚡ bash", onClick = onBash)  // Bash deshabilitado temporalmente
                OcChip("📎 archivo", onClick = onFileClick)
                OcChip(
                    "↩ undo",
                    onClick = onUndo,
                    color = Red,
                    borderColor = Red.copy(alpha = 0.3f),
                    bgColor = Red.copy(alpha = 0.07f),
                )
            }
            Spacer(Modifier.height(7.dp))
            if (selectedImageMimeType != null) {
                Row(
                    modifier = Modifier
                        .background(CardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, Green.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖼 Imagen seleccionada", style = MaterialTheme.typography.labelSmall, color = Green)
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.clickable { onClearImage() }) {
                        Text("✕", color = Red, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Escribe un prompt…", color = TextThird,
                        style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        cursorColor = Green,
                    ),
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                // Stop / Send
                if (isRunning) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Red.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, Red.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) { Text("⏹", fontSize = 18.sp) }
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Green, RoundedCornerShape(12.dp))
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) { Text("↑", fontSize = 18.sp, color = BgDark) }
                }
            }
        }
    }
}

@Composable
private fun BashDialog(
    input: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        title = { Text("⚡ Ejecutar comando bash") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("ej. ls -la / git status", color = TextThird, style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Green,
                    unfocusedBorderColor = BorderDark,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark,
                    cursorColor = Green,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onExecute,
                enabled = input.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = BgDark),
            ) { Text("Ejecutar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecond)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(
    request: PermissionRequest,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDeny,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderDark) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("⚡ Permiso requerido",
                style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("OpenCode quiere ejecutar: ${request.toolName}",
                style = MaterialTheme.typography.bodySmall, color = TextThird)
            Spacer(Modifier.height(16.dp))
            Surface(color = CardDark, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val lines = request.patterns.ifEmpty {
                        listOfNotNull(request.command, request.filePath, request.description.takeIf { it.isNotBlank() })
                    }
                    lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = Green)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Yellow.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Yellow.copy(alpha = 0.2f)),
            ) {
                Text(
                    "⚠️ Este comando ejecutará código en tu PC. Revisa antes de aprobar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Yellow,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderDark),
                ) { Text("Denegar", color = TextSecond) }
                Button(
                    onClick = onAllow,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = BgDark),
                ) { Text("✓ Permitir") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onAllowAlways,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Permitir siempre para esta herramienta",
                    style = MaterialTheme.typography.labelSmall, color = TextThird)
            }
        }
    }
}
