package com.zahidcodes.copynets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zahidcodes.copynets.ui.AddClipScreen
import com.zahidcodes.copynets.ui.ClipboardViewModel
import com.zahidcodes.copynets.ui.DetailScreen
import com.zahidcodes.copynets.ui.HomeScreen
import com.zahidcodes.copynets.ui.SettingsScreen
import com.zahidcodes.copynets.ui.theme.CopyNestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel = remember { ClipboardViewModel(context.applicationContext) }
            val themeMode by viewModel.themeMode.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.checkClipboardAndSave(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            CopyNestTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { clipId -> navController.navigate("detail/$clipId") },
                            onNavigateToAdd = { navController.navigate("add_clip") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable(
                        route = "detail/{clipId}",
                        arguments = listOf(navArgument("clipId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val clipId = backStackEntry.arguments?.getLong("clipId") ?: 0L
                        DetailScreen(
                            clipId = clipId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("add_clip") {
                        AddClipScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
