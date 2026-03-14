package com.opencode.android.ui.screens.models

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.model.Model
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModelsScreen(
    sessionId: String,
    currentModelId: String = "",
    onBack: () -> Unit,
    onModelSelected: (modelId: String, providerId: String) -> Unit,
    vm: ModelsViewModel = koinViewModel(parameters = { parametersOf(sessionId, currentModelId) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val providers = remember(state.providers, state.searchQuery) {
        val q = state.searchQuery.lowercase()
        if (q.isBlank()) state.providers
        else state.providers.map { p ->
            p.copy(models = p.models.filter { it.id.lowercase().contains(q) || it.name.lowercase().contains(q) })
        }.filter { it.models.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize().background(BgDark)) {
        Surface(color = SurfaceDark) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp)) {
                Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2190 Atras", style = MaterialTheme.typography.labelMedium, color = Green)
                }
                Spacer(Modifier.height(8.dp))
                Text("Modelo IA", style = MaterialTheme.typography.titleMedium)
                Text("Modelos autenticados en tu PC", style = MaterialTheme.typography.labelSmall, color = TextThird)
            }
        }
        Surface(color = SurfaceDark) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchChange,
                placeholder = { Text("Buscar modelo...", color = TextThird, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Green, unfocusedBorderColor = BorderDark,
                    focusedContainerColor = CardDark, unfocusedContainerColor = CardDark, cursorColor = Green),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            providers.forEach { provider ->
                item {
                    Row(
                        Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(
                            if (provider.isConnected) Green else TextThird,
                            RoundedCornerShape(50)
                        ))
                        Text(
                            provider.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (provider.isConnected) TextSecond else TextThird,
                            modifier = Modifier.weight(1f),
                        )
                        if (!provider.isConnected) {
                            Text("sin API key", style = MaterialTheme.typography.labelSmall, color = TextThird)
                        }
                    }
                }
                items(provider.models) { model ->
                    ModelRow(
                        model = model,
                        selected = state.selectedModelId == model.id,
                        enabled = provider.isConnected,
                        onClick = {
                            if (provider.isConnected) {
                                vm.selectModel(model.id, provider.id)
                                onModelSelected(model.id, provider.id)
                            }
                        }
                    )
                }
            }
            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green, modifier = Modifier.size(24.dp)) }}
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ModelRow(model: Model, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val textColor = if (enabled) TextPrimary else TextThird
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) GreenDim else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.id, style = MaterialTheme.typography.labelMedium, color = textColor)
            if (model.name.isNotBlank() && model.name != model.id)
                Text(model.name, style = MaterialTheme.typography.labelSmall, color = TextThird)
        }
        if (selected) Text("✓", style = MaterialTheme.typography.labelMedium, color = Green)
    }
}
