package com.zahidcodes.copynest

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zahidcodes.copynest.ui.AddClipScreen
import com.zahidcodes.copynest.ui.ClipboardViewModel
import com.zahidcodes.copynest.ui.DetailScreen
import com.zahidcodes.copynest.ui.HomeScreen
import com.zahidcodes.copynest.ui.SettingsScreen
import com.zahidcodes.copynest.ui.SplashScreen
import com.zahidcodes.copynest.ui.theme.CopyNestTheme

class MainActivity : ComponentActivity() {
    private var clipboardViewModel: ClipboardViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel = remember {
                ClipboardViewModel(context.applicationContext).also {
                    clipboardViewModel = it
                }
            }
            val themeMode by viewModel.themeMode.collectAsState()
            var hasCompletedSplash by rememberSaveable { mutableStateOf(false) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, hasCompletedSplash) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && hasCompletedSplash) {
                        viewModel.checkClipboardAndSave(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val windowInfo = LocalWindowInfo.current
            LaunchedEffect(windowInfo.isWindowFocused, hasCompletedSplash) {
                if (windowInfo.isWindowFocused && hasCompletedSplash) {
                    viewModel.checkClipboardAndSave(context)
                }
            }

            CopyNestTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val startRoute = remember { if (hasCompletedSplash) "home" else "splash" }

                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = {
                        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    }
                ) {
                    composable(
                        route = "splash",
                        exitTransition = {
                            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        }
                    ) {
                        SplashScreen(
                            onTimeout = {
                                hasCompletedSplash = true
                                viewModel.checkClipboardAndSave(context)
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(
                        route = "home",
                        enterTransition = {
                            fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
                        }
                    ) {
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post {
                clipboardViewModel?.checkClipboardAndSave(applicationContext)
            }
        }
    }
}
