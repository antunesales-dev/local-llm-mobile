package com.localllm.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class ChatColors(
    val userBubble: Color,
    val assistantBubble: Color,
    val toolBubble: Color,
    val onUserBubble: Color,
    val onAssistantBubble: Color,
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        userBubble = UserBubble,
        assistantBubble = AssistantBubble,
        toolBubble = ToolBubble,
        onUserBubble = OnPrimary,
        onAssistantBubble = OnSurface,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
)

private val LightChatColors = ChatColors(
    userBubble = UserBubble,
    assistantBubble = AssistantBubble,
    toolBubble = ToolBubble,
    onUserBubble = OnPrimary,
    onAssistantBubble = OnSurface,
)

private val DarkChatColors = ChatColors(
    userBubble = DarkUserBubble,
    assistantBubble = DarkAssistantBubble,
    toolBubble = DarkToolBubble,
    onUserBubble = DarkOnPrimaryContainer,
    onAssistantBubble = DarkOnSurface,
)

@Composable
fun LocalLlmChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val chatColors = if (darkTheme) DarkChatColors else LightChatColors

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
