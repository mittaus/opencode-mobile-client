package com.opencode.android.ui.screens.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.ui.components.*
import com.opencode.android.ui.theme.*
import com.opencode.shared.domain.model.UsageStats
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun StatsScreen(onBack: () -> Unit, vm: StatsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(BgDark)) {
        Surface(color = SurfaceDark) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp)) {
                Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2190 Chat", style = MaterialTheme.typography.labelMedium, color = Green)
                }
                Spacer(Modifier.height(8.dp))
                Text("Estadisticas", style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            // Period selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(Pair(7,"7 dias"), Pair(30,"30 dias"), Pair(null,"Todo")).forEach { (days, label) ->
                    val active = state.selectedPeriod == days
                    Box(Modifier.weight(1f)
                        .background(if (active) GreenDim else CardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, if (active) GreenMid else BorderDark, RoundedCornerShape(8.dp))
                        .clickable { vm.setPeriod(days) }.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (active) Green else TextThird)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
            } else {
                state.stats?.let { stats -> StatsContent(stats) }
                    ?: Text("Sin datos disponibles", style = MaterialTheme.typography.bodySmall,
                        color = TextThird, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun StatsContent(stats: UsageStats) {
    // Row: tokens + cost
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Tokens usados", formatTokens(stats.totalInputTokens + stats.totalOutputTokens), "entrada + salida", Modifier.weight(1f))
        StatCard("Costo est.", String.format(Locale.US, "\$%.2f", stats.estimatedCostUsd), "USD total", Modifier.weight(1f), valueColor = Green)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Sesiones", stats.sessionCount.toString(), "proyectos distintos", Modifier.weight(1f))
        StatCard("Prompts", stats.promptCount.toString(), "prompts enviados", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))

    if (stats.usageByModel.isNotEmpty()) {
        BarSection(title = "Uso por modelo",
            items = stats.usageByModel.map { Triple(it.modelId, it.percentage, Green) })
    }
    if (stats.usageByTool.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        BarSection(title = "Herramientas mas usadas",
            items = stats.usageByTool.take(5).map { Triple(it.toolName, it.percentage, Blue) })
    }
}

@Composable
private fun StatCard(
    label: String, value: String, sub: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Column(modifier.background(CardDark, RoundedCornerShape(12.dp))
        .border(1.dp, BorderDark, RoundedCornerShape(12.dp)).padding(14.dp)) {
        MonoLabel(label)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = TextThird, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun BarSection(title: String, items: List<Triple<String, Float, androidx.compose.ui.graphics.Color>>) {
    Column(Modifier.fillMaxWidth().background(CardDark, RoundedCornerShape(12.dp))
        .border(1.dp, BorderDark, RoundedCornerShape(12.dp)).padding(16.dp)) {
        MonoLabel(title)
        items.forEach { (name, pct, color) ->
            Row(Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Box(Modifier.width(80.dp).height(4.dp).background(BorderDark, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(color, RoundedCornerShape(4.dp)))
                }
                Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextThird,
                    modifier = Modifier.width(32.dp))
            }
            HorizontalDivider(color = BorderDark, thickness = 1.dp)
        }
    }
}

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}
