package com.localllm.chat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.localllm.chat.ui.chat.ChatScreen
import com.localllm.chat.ui.home.HomeScreen
import com.localllm.chat.ui.models.ModelScreen

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{conversationId}"
    const val MODELS = "models"

    fun chat(conversationId: Long) = "chat/$conversationId"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onNavigateToModels = {
                    navController.navigate(Routes.MODELS)
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType }),
        ) {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MODELS) {
            ModelScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
