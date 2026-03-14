package com.opencode.android.ui.screens.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    vm: ConnectViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.navigateToProjects.collect { onConnected() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 32.dp),
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = GreenDim,
                tonalElevation = 0.dp,
                modifier = Modifier.size(48.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("⚡", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("OpenCode", style = MaterialTheme.typography.titleLarge)
                Text("remote vibe coding", style = MaterialTheme.typography.labelSmall,
                    color = TextThird)
            }
        }

        Spacer(Modifier.height(36.dp))

        MonoLabel("IP de tu computadora")
        OcTextField(
            value = state.host,
            onValueChange = vm::onHostChange,
            placeholder = "192.168.1.100",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Spacer(Modifier.height(16.dp))
        MonoLabel("Puerto")
        OcTextField(
            value = state.port,
            onValueChange = vm::onPortChange,
            placeholder = "8080",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(Modifier.height(24.dp))

        // Command hint
        Surface(
            color = CardDark,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("CORRE ESTO EN TU PC PRIMERO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextThird)
                Spacer(Modifier.height(6.dp))
                MonoCode(
                    "# .env del gateway:\n" +
                    "GATEWAY_API_KEY=tu-clave-secreta\n\n" +
                    "$ .\\opencode-gateway.exe"
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        MonoLabel("API Key (X-API-Key)")
        var apiKeyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = vm::onApiKeyChange,
            placeholder = { Text("tu-api-key-del-gateway", color = TextThird, style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = BorderDark,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                cursorColor = Green,
            ),
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Text(
                        text = if (apiKeyVisible) "🙈" else "👁",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = Red, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        OcPrimaryButton(
            text = if (state.isLoading) "Conectando…" else "Conectar →",
            enabled = !state.isLoading,
            onClick = vm::connect,
        )
    }
}
