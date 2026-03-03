package com.localllm.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.localllm.chat.ui.navigation.NavGraph
import com.localllm.chat.ui.theme.LocalLlmChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalLlmChatTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
