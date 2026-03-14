package com.opencode.android.ui.screens.files

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.model.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesScreen(
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    vm: FilesViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(BgDark).imePadding()) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Surface(color = SurfaceDark) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambios", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        OcIconButton("\u21BB", vm::load)
                    }
                    val totalAdd = state.diffs.sumOf { it.additions }
                    val totalDel = state.diffs.sumOf { it.deletions }
                    if (totalAdd > 0 || totalDel > 0) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("+$totalAdd", style = MaterialTheme.typography.labelMedium, color = Green)
                            Text("-$totalDel", style = MaterialTheme.typography.labelMedium, color = Red)
                        }
                    }
                }
            }

            // ── File list ─────────────────────────────────────────────────────
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    state.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Green, modifier = Modifier.size(24.dp))
                        }
                    }
                    state.diffs.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Sin cambios", style = MaterialTheme.typography.bodySmall, color = TextThird)
                        }
                    }
                    else -> {
                        // Staged Changes section
                        if (state.staged.isNotEmpty()) {
                            item { SectionHeader("Staged Changes", state.staged.size) }
                            items(state.staged, key = { "s_${it.path}" }) { diff ->
                                FileRow(
                                    diff = diff,
                                    actionIcon = "−",
                                    onAction = { vm.unstage(diff.path) },
                                    onOpenDiff = { vm.openDiff(diff) },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        // Changes section
                        if (state.unstaged.isNotEmpty()) {
                            item { SectionHeader("Changes", state.unstaged.size) }
                            items(state.unstaged, key = { "u_${it.path}" }) { diff ->
                                FileRow(
                                    diff = diff,
                                    actionIcon = "+",
                                    onAction = { vm.stage(diff.path) },
                                    onOpenDiff = { vm.openDiff(diff) },
                                    onDiscard = { vm.discard(diff.path, diff.status.name.lowercase()) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Commit footer ─────────────────────────────────────────────────
            if (!state.isLoading && state.diffs.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OcTextField(
                        value = state.commitMessage,
                        onValueChange = vm::onCommitMessageChange,
                        placeholder = "Mensaje de commit…",
                    )
                    val stagedCount = state.staged.size
                    val buttonLabel = when {
                        state.isCommitting -> "Committing…"
                        stagedCount > 0 -> "Commit ($stagedCount staged)"
                        else -> "Commit todo (${state.diffs.size} archivos)"
                    }
                    OcPrimaryButton(
                        text = buttonLabel,
                        onClick = vm::commit,
                        enabled = state.commitMessage.isNotBlank() && !state.isCommitting,
                    )
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Red)
                    }
                }
            }

            OcBottomNav("files", onChat = onBack, onFiles = {}, onStats = onOpenStats)
            HomeBar()
        }

        state.selectedDiff?.let { diff ->
            DiffDetailPanel(diff = diff, onClose = vm::closeDiff)
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecond,
            modifier = Modifier.weight(1f))
        Box(
            Modifier.background(TextThird.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.labelSmall, color = TextSecond)
        }
    }
}

// ── File row ───────────────────────────────────────────────────────────────────

private val IconColor = androidx.compose.ui.graphics.Color(0xFFB0B0C8)

@Composable
private fun FileRow(
    diff: FileDiff,
    actionIcon: String,
    onAction: () -> Unit,
    onOpenDiff: () -> Unit,
    onDiscard: (() -> Unit)? = null,
) {
    val isDeleted = diff.status == FileStatus.DELETED
    val statusColor = when (diff.status) {
        FileStatus.ADDED   -> Green
        FileStatus.DELETED -> Red
        FileStatus.RENAMED -> Blue
        else               -> androidx.compose.ui.graphics.Color(0xFFE8A055) // orange-ish for M
    }
    val statusLabel = when (diff.status) {
        FileStatus.ADDED   -> "A"
        FileStatus.DELETED -> "D"
        FileStatus.RENAMED -> "R"
        else               -> "M"
    }

    Row(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenDiff)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // File name + path
        Column(Modifier.weight(1f)) {
            val fileName = diff.path.split("/").lastOrNull() ?: diff.path
            Text(
                fileName,
                style = if (isDeleted)
                    MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.LineThrough)
                else
                    MaterialTheme.typography.labelMedium,
                color = if (isDeleted) TextThird else TextPrimary,
            )
            val dir = diff.path.substringBeforeLast("/", "")
            if (dir.isNotEmpty()) {
                Text(dir, style = MaterialTheme.typography.labelSmall, color = TextThird,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }

        // +/- counts
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (diff.additions > 0) Text("+${diff.additions}", style = MaterialTheme.typography.labelSmall, color = Green)
            if (diff.deletions > 0) Text("-${diff.deletions}", style = MaterialTheme.typography.labelSmall, color = Red)
        }

        // Status letter (A / M / D)
        Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor,
            modifier = Modifier.width(14.dp))

        // Discard button (only for unstaged files)
        if (onDiscard != null) {
            DiscardIconButton(onDiscard)
        }

        // Stage / Unstage button
        ActionIconButton(actionIcon, onAction)
    }
}

@Composable
private fun ActionIconButton(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp)
            .background(IconColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, style = MaterialTheme.typography.titleSmall, color = IconColor)
    }
}

@Composable
private fun DiscardIconButton(onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp)
            .background(IconColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Undo,
            contentDescription = "Descartar cambios",
            tint = IconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Diff detail panel ─────────────────────────────────────────────────────────

@Composable
private fun DiffDetailPanel(diff: FileDiff, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgDark)) {
        Surface(color = SurfaceDark) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp)) {
                Row(Modifier.clickable(onClick = onClose), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2190 Volver", style = MaterialTheme.typography.labelMedium, color = Green)
                }
                Spacer(Modifier.height(8.dp))
                Text(diff.path, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("+${diff.additions}", style = MaterialTheme.typography.labelSmall, color = Green)
                    Text("-${diff.deletions}", style = MaterialTheme.typography.labelSmall, color = Red)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).background(androidx.compose.ui.graphics.Color(0xFF080810))) {
            diff.hunks.forEach { hunk ->
                item {
                    Box(Modifier.fillMaxWidth().background(Blue.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 3.dp)) {
                        Text(hunk.header, style = MaterialTheme.typography.bodySmall, color = Blue)
                    }
                }
                items(hunk.lines) { line ->
                    val bg = when (line.type) {
                        DiffLineType.ADDITION -> Green.copy(alpha = 0.07f)
                        DiffLineType.DELETION -> Red.copy(alpha = 0.07f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                    val tc = when (line.type) {
                        DiffLineType.ADDITION -> androidx.compose.ui.graphics.Color(0xFF7EE8B0)
                        DiffLineType.DELETION -> androidx.compose.ui.graphics.Color(0xFFF08090)
                        else -> TextThird
                    }
                    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${line.oldNumber ?: line.newNumber ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (line.type) { DiffLineType.ADDITION -> Green; DiffLineType.DELETION -> Red; else -> TextThird },
                            modifier = Modifier.width(28.dp))
                        Text(line.content, style = MaterialTheme.typography.bodySmall, color = tc,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        HomeBar()
    }
}
