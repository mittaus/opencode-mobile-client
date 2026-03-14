package com.opencode.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencode.android.ui.screens.chat.ChatScreen
import com.opencode.android.ui.screens.connect.ConnectScreen
import com.opencode.android.ui.screens.files.FilesScreen
import com.opencode.android.ui.screens.models.ModelsScreen
import com.opencode.android.ui.screens.projects.ProjectsScreen
import com.opencode.android.ui.screens.sessions.SessionsScreen
import com.opencode.android.ui.screens.stats.StatsScreen

sealed class Route(val path: String) {
    data object Connect   : Route("connect")
    data object Projects  : Route("projects")
    data object Models    : Route("models/{sessionId}/{currentModelId}") {
        fun go(sessionId: String, currentModelId: String = "") =
            "models/$sessionId/${java.net.URLEncoder.encode(currentModelId.ifBlank { "none" }, "UTF-8")}"
    }
    data object Chat      : Route("chat/{sessionId}/{projectPath}") {
        fun go(sessionId: String, projectPath: String) =
            "chat/$sessionId/${java.net.URLEncoder.encode(projectPath, "UTF-8")}"
    }
    data object Files     : Route("files")
    data object Sessions  : Route("sessions/{worktree}/{projectId}") {
        fun go(worktree: String, projectId: String = "global") =
            "sessions/${java.net.URLEncoder.encode(worktree, "UTF-8")}/${java.net.URLEncoder.encode(projectId, "UTF-8")}"
    }
    data object Stats     : Route("stats")
}

@Composable
fun OpenCodeNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.Connect.path,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Connect.path) {
            ConnectScreen(
                onConnected = { navController.navigate(Route.Projects.path) }
            )
        }
        composable(Route.Projects.path) {
            ProjectsScreen(
                onBack = { navController.popBackStack() },
                onSelectProject = { worktree, projectId ->
                    navController.navigate(Route.Sessions.go(worktree, projectId))
                },
            )
        }
        composable(
            route = Route.Models.path,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("currentModelId") { type = NavType.StringType },
            ),
        ) { back ->
            val sessionId      = back.arguments?.getString("sessionId") ?: ""
            val currentModelId = java.net.URLDecoder.decode(back.arguments?.getString("currentModelId") ?: "none", "UTF-8")
                .takeIf { it != "none" } ?: ""
            ModelsScreen(
                sessionId = sessionId,
                currentModelId = currentModelId,
                onBack = { navController.popBackStack() },
                onModelSelected = { modelId, providerId ->
                    // Devolver el modelo elegido al ChatScreen vía savedStateHandle
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selectedModelId", modelId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selectedProviderId", providerId)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Route.Chat.path,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType },
            ),
        ) { back ->
            val sessionId   = back.arguments?.getString("sessionId") ?: ""
            val projectPath = java.net.URLDecoder.decode(back.arguments?.getString("projectPath") ?: "", "UTF-8")

            // Recibir modelo elegido desde ModelsScreen
            val savedState = back.savedStateHandle
            val returnedModelId    = savedState.get<String>("selectedModelId")
            val returnedProviderId = savedState.get<String>("selectedProviderId")

            ChatScreen(
                sessionId      = sessionId,
                projectPath    = projectPath,
                returnedModelId    = returnedModelId,
                returnedProviderId = returnedProviderId,
                onModelConsumed    = {
                    savedState.remove<String>("selectedModelId")
                    savedState.remove<String>("selectedProviderId")
                },
                onOpenFiles    = { navController.navigate(Route.Files.path) },
                onOpenStats    = { navController.navigate(Route.Stats.path) },
                onChangeModel  = { navController.navigate(Route.Models.go(sessionId, it)) },
            )
        }
        composable(Route.Files.path) {
            FilesScreen(
                onBack      = { navController.popBackStack() },
                onOpenStats = { navController.navigate(Route.Stats.path) },
            )
        }
        composable(
            route = Route.Sessions.path,
            arguments = listOf(
                navArgument("worktree") { type = NavType.StringType },
                navArgument("projectId") { type = NavType.StringType },
            ),
        ) { back ->
            val worktree = java.net.URLDecoder.decode(back.arguments?.getString("worktree") ?: "", "UTF-8")
            val projectId = java.net.URLDecoder.decode(back.arguments?.getString("projectId") ?: "global", "UTF-8")
            SessionsScreen(
                projectPath = worktree,
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onOpenChat = { id -> navController.navigate(Route.Chat.go(id, worktree)) },
                onNewSession = { id -> navController.navigate(Route.Chat.go(id, worktree)) },
            )
        }
        composable(Route.Stats.path) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
