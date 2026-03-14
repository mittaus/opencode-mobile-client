package com.opencode.android.ui.screens.sessions

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.model.Session
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionsScreen(
    projectPath: String,
    projectId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewSession: (String) -> Unit,
    vm: SessionsViewModel = koinViewModel(parameters = { parametersOf(projectPath, projectId) }),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.openChat.collect { onNewSession(it) } }
    Column(Modifier.fillMaxSize().background(BgDark)) {
        Surface(color = SurfaceDark) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp)) {
                Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2190 Proyectos", style = MaterialTheme.typography.labelMedium, color = Green)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sesiones", style = MaterialTheme.typography.titleMedium)
                        Text(
                            projectPath.substringAfterLast('\\').substringAfterLast('/').ifEmpty { projectPath },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextThird,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    OcIconButton("\u21BB", vm::load)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Box(Modifier.fillMaxWidth()
                    .background(GreenDim, RoundedCornerShape(12.dp))
                    .border(1.dp, GreenMid, RoundedCornerShape(12.dp))
                    .clickable(enabled = !state.isCreatingSession, onClick = vm::newSession)
                    .padding(14.dp),
                    contentAlignment = Alignment.Center) {
                    if (state.isCreatingSession) {
                        CircularProgressIndicator(color = Green, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("\uFF0B Nueva sesión", style = MaterialTheme.typography.labelMedium, color = Green)
                    }
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green, modifier = Modifier.size(24.dp)) }}
            } else {
                items(state.sessions) { session ->
                    SessionCard(session, onClick = { onOpenChat(session.id) })
                }
            }
        }
        HomeBar()
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()
        .background(if (session.isActive) GreenDim else CardDark, RoundedCornerShape(12.dp))
        .border(1.dp, if (session.isActive) GreenMid else BorderDark, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.title, style = MaterialTheme.typography.labelMedium, color = TextPrimary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (session.isActive) Box(Modifier.size(6.dp).background(Green, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = SurfaceDark, shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, BorderDark)) {
                Text(session.modelId.take(24), style = MaterialTheme.typography.labelSmall, color = Purple,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text("\uD83D\uDCC1 ${session.projectId.take(8)}",
                style = MaterialTheme.typography.labelSmall, color = TextThird)
        }
    }
}
