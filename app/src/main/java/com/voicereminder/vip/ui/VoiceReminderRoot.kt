package com.voicereminder.vip.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.ui.screens.AlarmEditorScreen
import com.voicereminder.vip.ui.screens.AssistantScreen
import com.voicereminder.vip.ui.screens.CategoriesScreen
import com.voicereminder.vip.ui.screens.HistoryScreen
import com.voicereminder.vip.ui.screens.HomeScreen
import com.voicereminder.vip.ui.screens.RoutineEditorScreen
import com.voicereminder.vip.ui.screens.RoutinesScreen
import com.voicereminder.vip.ui.screens.SettingsScreen
import com.voicereminder.vip.ui.screens.TemplatesScreen
import com.voicereminder.vip.ui.screens.VoiceStudioScreen
import com.voicereminder.vip.ui.vm.AlarmEditorViewModel
import com.voicereminder.vip.ui.vm.AlarmEditorViewModelFactory
import com.voicereminder.vip.ui.vm.AppViewModel

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem("home", "الرئيسية", Icons.Outlined.Home),
    BottomItem("routines", "الروتين", Icons.Outlined.Repeat),
    BottomItem("studio", "الأصوات", Icons.Outlined.GraphicEq),
    BottomItem("history", "السجل", Icons.Outlined.History),
    BottomItem("settings", "الإعدادات", Icons.Outlined.Settings)
)

@Composable
fun VoiceReminderRoot(app: AppViewModel, container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    LaunchedEffect(Unit) {
        app.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BottomBar(currentRoute, navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    app = app,
                    container = container,
                    onAddAlarm = { navController.navigate("editor/0") },
                    onEditAlarm = { id -> navController.navigate("editor/$id") },
                    onOpenTemplates = { navController.navigate("templates") },
                    onOpenCategories = { navController.navigate("categories") },
                    onOpenAssistant = { navController.navigate("assistant") },
                    onOpenStudio = { navController.navigate("studio") },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }

            composable("routines") {
                RoutinesScreen(
                    app = app,
                    onAddRoutine = { navController.navigate("routine_editor/0") },
                    onEditRoutine = { id -> navController.navigate("routine_editor/$id") }
                )
            }

            composable("studio") {
                VoiceStudioScreen(app = app, container = container)
            }

            composable("history") {
                HistoryScreen(app = app)
            }

            composable("settings") {
                SettingsScreen(app = app, container = container)
            }

            composable("templates") {
                TemplatesScreen(
                    app = app,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("categories") {
                CategoriesScreen(
                    app = app,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("assistant") {
                AssistantScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { draft ->
                        app.setPendingDraft(draft)
                        navController.navigate("editor/0")
                    }
                )
            }

            composable(
                route = "editor/{alarmId}",
                arguments = listOf(navArgument("alarmId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("alarmId") ?: 0L
                val prefill = remember(entry.id) {
                    if (id == 0L) app.takePendingDraft() else null
                }
                val factory = remember(entry.id) {
                    AlarmEditorViewModelFactory(container, id, prefill)
                }
                val editorViewModel: AlarmEditorViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    factory = factory
                )
                AlarmEditorScreen(
                    viewModel = editorViewModel,
                    app = app,
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "routine_editor/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { entry ->
                val groupId = entry.arguments?.getLong("groupId") ?: 0L
                RoutineEditorScreen(
                    app = app,
                    container = container,
                    groupId = groupId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute == item.route) return@NavigationBarItem
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
