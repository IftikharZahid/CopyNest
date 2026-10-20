package com.zahidcodes.copynest

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.zahidcodes.copynest.update.InAppUpdateManager
import com.zahidcodes.copynest.update.SplashUpdateUiState

class MainActivity : ComponentActivity() {
    private var clipboardViewModel: ClipboardViewModel? = null
    private var inAppUpdateManager: InAppUpdateManager? = null
    private var isSplashAndUpdateCompleted: Boolean = false

    private val updateActivityResultLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            inAppUpdateManager?.onUpdateActivityResult(
                resultCode = result.resultCode,
                launcher = updateActivityResultLauncher
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val updateManager = InAppUpdateManager(applicationContext).also {
            inAppUpdateManager = it
        }
        updateManager.checkForUpdates(updateActivityResultLauncher)

        setContent {
            val context = LocalContext.current
            val viewModel = remember {
                ClipboardViewModel(context.applicationContext).also {
                    clipboardViewModel = it
                }
            }
            val themeMode by viewModel.themeMode.collectAsState()
            val updateUiState by updateManager.uiState.collectAsState()
            var hasCompletedSplash by rememberSaveable { mutableStateOf(false) }

            val isAllowed = hasCompletedSplash && updateUiState is SplashUpdateUiState.AllowedToProceed
            val currentIsAllowed by rememberUpdatedState(isAllowed)
            LaunchedEffect(isAllowed) {
                isSplashAndUpdateCompleted = isAllowed
                if (isAllowed) {
                    viewModel.onAppForegrounded(context)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                var isInitialResume = true
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (isInitialResume) {
                            isInitialResume = false
                        } else {
                            updateManager.onResume(updateActivityResultLauncher)
                        }
                    }
                    if ((event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) &&
                        currentIsAllowed
                    ) {
                        viewModel.onAppForegrounded(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val windowInfo = LocalWindowInfo.current
            LaunchedEffect(windowInfo.isWindowFocused, isAllowed) {
                if (windowInfo.isWindowFocused && isAllowed) {
                    viewModel.onAppForegrounded(context)
                }
            }

            val showSplashGate = !hasCompletedSplash || updateUiState !is SplashUpdateUiState.AllowedToProceed

            CopyNestTheme(themeMode = themeMode, isSplashScreen = showSplashGate) {
                if (showSplashGate) {
                    SplashScreen(
                        onTimeout = {
                            hasCompletedSplash = true
                        },
                        updateUiState = updateUiState,
                        onRetryUpdate = {
                            updateManager.retryUpdate(updateActivityResultLauncher)
                        },
                        onOpenPlayStore = {
                            updateManager.openPlayStoreListing(this@MainActivity)
                        },
                        onCompleteDownloadedUpdate = {
                            updateManager.completeDownloadedUpdate()
                        },
                        onDismissOptionalUpdate = {
                            updateManager.dismissOptionalUpdate()
                        },
                        onExitApp = {
                            finishAffinity()
                        }
                    )
                } else {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                        }
                    ) {
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isSplashAndUpdateCompleted) {
            window.decorView.post {
                if (isSplashAndUpdateCompleted) {
                    clipboardViewModel?.onAppForegrounded(applicationContext)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdateManager?.onCleared()
    }
}
