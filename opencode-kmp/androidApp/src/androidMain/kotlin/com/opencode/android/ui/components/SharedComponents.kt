package com.opencode.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.opencode.android.R
import com.opencode.android.ui.theme.*

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextThird,
        modifier = modifier.padding(bottom = 7.dp),
        letterSpacing = 0.08.sp,
    )
}

@Composable
fun OcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextThird, style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Green,
            unfocusedBorderColor = BorderDark,
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark,
            cursorColor = Green,
        ),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun OcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Green,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color(0xFF060610),
            disabledContainerColor = color.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun MonoCode(text: String, color: Color = TextSecond, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

@Composable
fun OcChip(
    label: String,
    onClick: () -> Unit = {},
    color: Color = TextSecond,
    borderColor: Color = BorderDark,
    bgColor: Color = CardDark,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun OcIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(CardDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 14.sp)
    }
}

@Composable
fun OcIconButton(
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(CardDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextThird,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        letterSpacing = 0.08.sp,
    )
}

@Composable
fun HomeBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(100.dp).height(4.dp).background(BorderDark, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun OcBottomNav(
    activeRoute: String,
    onChat: () -> Unit,
    onFiles: () -> Unit,
    onStats: () -> Unit,
) {
    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 0.dp,
    ) {
        val items = listOf(
            Triple("chat", "chat", onChat),
            Triple("files", "cambios", onFiles),
            Triple("stats", "stats", onStats)
        )
        items.forEach { (id, label, action) ->
            val isActive = activeRoute == id
            NavigationBarItem(
                selected = isActive,
                onClick = action,
                icon = { 
                    when (id) {
                        "chat" -> Text("💬", fontSize = 16.sp)
                        "files" -> Icon(
                            painter = painterResource(com.opencode.android.R.drawable.ic_git),
                            contentDescription = label,
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) Green else TextThird
                        )
                        "stats" -> Text("📊", fontSize = 16.sp)
                    }
                },
                label = {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) Green else TextThird)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green,
                    indicatorColor = GreenDim,
                ),
            )
        }
    }
}

@Composable
fun ToolCallBubble(icon: String, label: String, accent: Color = Blue) {
    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, fontSize = 13.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .background(CardDark, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(Modifier.size(5.dp).background(TextThird, RoundedCornerShape(50)))
        }
    }
}
