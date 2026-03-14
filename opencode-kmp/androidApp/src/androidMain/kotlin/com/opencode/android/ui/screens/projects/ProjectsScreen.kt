package com.opencode.android.ui.screens.projects

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.repository.AvailableProject
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onSelectProject: (worktree: String, projectId: String) -> Unit,
    vm: ProjectsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showRegisterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.navigateTo.collect { (worktree, projectId) -> onSelectProject(worktree, projectId) }
    }

    if (showRegisterDialog) {
        RegisterProjectDialog(
            isLoading = state.isRegistering,
            onConfirm = { directory ->
                vm.registerProject(directory)
                showRegisterDialog = false
            },
            onDismiss = { showRegisterDialog = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BgDark)) {
            Surface(color = SurfaceDark) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp)) {
                    Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                        Text("← Conexion", style = MaterialTheme.typography.labelMedium, color = Green)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Proyectos", style = MaterialTheme.typography.titleMedium)
                    Text("Elige el directorio de trabajo", style = MaterialTheme.typography.labelSmall, color = TextThird)
                }
            }
            LazyColumn(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SectionLabel("Recientes en tu PC") }
                items(state.projects) { project ->
                    ProjectCard(
                        project = project,
                        isActive = project.worktree == state.activeWorktree,
                        onClick = { vm.selectProject(project) },
                    )
                }
                if (state.isLoading) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Green, modifier = Modifier.size(24.dp))
                    }
                }
                state.error?.let { err ->
                    item {
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp))
                    }
                }
            }
            HomeBar()
        }

        FloatingActionButton(
            onClick = { showRegisterDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 80.dp),
            containerColor = Green,
            contentColor = BgDark,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Registrar proyecto")
        }
    }
}

@Composable
private fun RegisterProjectDialog(
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var directory by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        title = { Text("Nueva sesión", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Ingresa la ruta del proyecto en el servidor",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextThird,
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    placeholder = { Text("/home/user/my-project", color = TextThird) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Green,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (directory.isNotBlank()) onConfirm(directory.trim()) },
                enabled = directory.isNotBlank() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Green, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear sesión", color = Green)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecond)
            }
        },
    )
}

@Composable
private fun ProjectCard(project: AvailableProject, isActive: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (isActive) GreenDim else CardDark, RoundedCornerShape(12.dp))
            .border(1.dp, if (isActive) Green else BorderDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp)
                .background(
                    if (isActive) Green.copy(alpha = 0.15f) else Blue.copy(alpha = 0.1f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isActive) "\uD83D\uDCC2" else "\uD83D\uDCC1")
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) Green else TextPrimary,
            )
            Text(project.path, style = MaterialTheme.typography.labelSmall, color = TextThird, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isActive) {
                    Text("● activo", style = MaterialTheme.typography.labelSmall, color = Green)
                }
                if (project.isRunning) {
                    Text("● running :${project.port}", style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) Green else TextSecond)
                }
                if (project.isRegistered) {
                    Text("● REGISTERED", style = MaterialTheme.typography.labelSmall, color = Green)
                } else {
                    Text("○ SESSION", style = MaterialTheme.typography.labelSmall, color = TextThird)
                }
            }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = if (isActive) Green else TextThird)
    }
}
