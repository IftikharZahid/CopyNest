package com.zahidcodes.copynest.ui

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.zahidcodes.copynest.R
import com.zahidcodes.copynest.update.SplashUpdateUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Brand palette derived from the CopyNest dark navy & gold identity
private val BrandNavyCenter = Color(0xFF07162C)
private val BrandNavyMid = Color(0xFF051122)
private val BrandNavyDeep = Color(0xFF04101E)
private val BrandNavyCardBg = Color(0xFF041228)
private val BrandCopyWhiteBlue = Color(0xFFF0F7FF)
private val BrandNestGold = Color(0xFFFBBF24)
private val BrandNestGoldLight = Color(0xFFFDE68A)
private val BrandNestGoldDeep = Color(0xFFF59E0B)
private val BrandAmberDark = Color(0xFFD97706)
private val BrandSlateDark = Color(0xFF0F172A)
private val BrandSlateMuted = Color(0xFF475569)

// Smooth Easing Curves
private val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val SubtleSettleEasing = CubicBezierEasing(0.34f, 1.35f, 0.64f, 1.0f)

@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    updateUiState: SplashUpdateUiState = SplashUpdateUiState.AllowedToProceed,
    onRetryUpdate: () -> Unit = {},
    onOpenPlayStore: () -> Unit = {},
    onCompleteDownloadedUpdate: () -> Unit = {},
    onDismissOptionalUpdate: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density

    // Prevent Back navigation from bypassing a mandatory update while on the splash screen
    BackHandler(enabled = updateUiState !is SplashUpdateUiState.AllowedToProceed) {
        if (updateUiState is SplashUpdateUiState.UpdateRequiredBlocked) {
            onExitApp()
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false

        // On white background, status bar and nav bar icons must be dark (visible)
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true

        onDispose {
            controller?.isAppearanceLightStatusBars = prevLightStatus
            controller?.isAppearanceLightNavigationBars = prevLightNav
        }
    }

    val reduceMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale < 0.1f
        } catch (_: Exception) {
            false
        }
    }

    val currentOnTimeout by rememberUpdatedState(onTimeout)
    val currentUpdateUiState by rememberUpdatedState(updateUiState)
    var splashAnimationFinished by rememberSaveable { mutableStateOf(false) }
    val timelineMs = remember { Animatable(if (reduceMotion) 1250f else 0f) }

    LaunchedEffect(reduceMotion) {
        if (splashAnimationFinished) return@LaunchedEffect

        if (reduceMotion) {
            timelineMs.snapTo(1250f)
            delay(250L)
            splashAnimationFinished = true
            if (currentUpdateUiState is SplashUpdateUiState.AllowedToProceed) {
                currentOnTimeout()
            }
            return@LaunchedEffect
        }

        timelineMs.snapTo(0f)
        timelineMs.animateTo(
            targetValue = 1350f,
            animationSpec = tween(
                durationMillis = 1350,
                easing = LinearEasing
            )
        )
        splashAnimationFinished = true
        if (currentUpdateUiState is SplashUpdateUiState.AllowedToProceed) {
            currentOnTimeout()
        }
    }

    // Navigate to main app ONLY when the splash animation has finished AND mandatory update verification allows proceeding
    if (splashAnimationFinished && updateUiState is SplashUpdateUiState.AllowedToProceed) {
        SideEffect {
            currentOnTimeout()
        }
    }
    LaunchedEffect(splashAnimationFinished, updateUiState) {
        if (splashAnimationFinished && updateUiState is SplashUpdateUiState.AllowedToProceed) {
            currentOnTimeout()
        }
    }

    val t = timelineMs.value

    // Logo is immediately present at full opacity (alpha = 1f) from frame 0 so there is never an empty screen
    val iconScale = when {
        t <= 550f -> {
            val p = SmoothOutEasing.transform((t / 550f).coerceIn(0f, 1f))
            lerpFloat(0.86f, 1.02f, p)
        }
        t <= 850f -> {
            val p = SubtleSettleEasing.transform(((t - 550f) / 300f).coerceIn(0f, 1f))
            lerpFloat(1.02f, 1.00f, p)
        }
        else -> 1.00f
    }

    val iconAlpha = 1.0f

    val iconTranslationYDp = when {
        t <= 500f -> {
            val p = SmoothOutEasing.transform((t / 500f).coerceIn(0f, 1f))
            lerpFloat(6f, 0f, p)
        }
        else -> 0f
    }

    // CopyNest Wordmark & Golden Underline are visible right from the start and smoothly settle
    val wordmarkProgress = SmoothOutEasing.transform(((t - 60f) / 480f).coerceIn(0f, 1f))
    val wordmarkAlpha = lerpFloat(0.75f, 1.0f, wordmarkProgress)
    val wordmarkScale = lerpFloat(0.94f, 1.00f, wordmarkProgress)
    val wordmarkTranslationYDp = lerpFloat(6f, 0f, wordmarkProgress)

    val underlineProgress = SmoothOutEasing.transform(((t - 120f) / 550f).coerceIn(0.18f, 1f))

    val lightSweepProgress = ((t - 650f) / 500f).coerceIn(0f, 1f)
    val lightSweepAlpha = if (lightSweepProgress <= 0f || lightSweepProgress >= 1f) {
        0f
    } else {
        kotlin.math.sin(lightSweepProgress * Math.PI.toFloat()) * 0.24f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer(
                        scaleX = iconScale,
                        scaleY = iconScale,
                        alpha = iconAlpha,
                        translationY = iconTranslationYDp * density,
                        cameraDistance = 14f * density
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(142.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(32.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_fg_art),
                            contentDescription = "CopyNest Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .testTag("splash_logo_image")
                        )

                        if (lightSweepAlpha > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sweepCenter = size.width * lightSweepProgress
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            BrandNestGoldLight.copy(alpha = lightSweepAlpha),
                                            Color.Transparent
                                        ),
                                        start = Offset(sweepCenter - size.width * 0.25f, 0f),
                                        end = Offset(sweepCenter + size.width * 0.25f, size.height)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(
                    alpha = wordmarkAlpha,
                    scaleX = wordmarkScale,
                    scaleY = wordmarkScale,
                    translationY = wordmarkTranslationYDp * density
                )
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = BrandSlateDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append("Copy")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = BrandAmberDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append("Nest")
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_wordmark_text")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(124.dp)
                        .height(4.dp)
                        .testTag("splash_golden_underline"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(underlineProgress.coerceIn(0.18f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFBBF24),
                                        Color(0xFFD97706),
                                        Color(0xFFB45309)
                                    )
                                )
                            )
                    )
                }
            }

            // Integrated In-App Update Status & Mandatory Force-Update Section
            AnimatedVisibility(
                visible = updateUiState !is SplashUpdateUiState.AllowedToProceed &&
                    updateUiState !is SplashUpdateUiState.Checking,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    when (val state = updateUiState) {
                        is SplashUpdateUiState.Checking -> Unit

                        is SplashUpdateUiState.PreparingImmediateUpdate -> {
                            SplashStatusIndicator(statusText = state.message)
                        }

                        is SplashUpdateUiState.ImmediateUpdateInProgress -> {
                            SplashDownloadProgressSection(
                                statusMessage = state.statusMessage,
                                bytesDownloaded = state.bytesDownloaded,
                                totalBytesToDownload = state.totalBytesToDownload
                            )
                        }

                        is SplashUpdateUiState.UpdateDownloadedRestartRequired -> {
                            SplashRestartRequiredCard(
                                message = state.message,
                                onCompleteUpdate = onCompleteDownloadedUpdate
                            )
                        }

                        is SplashUpdateUiState.UpdateRequiredBlocked -> {
                            SplashForceUpdateBlockedCard(
                                state = state,
                                onRetryUpdate = onRetryUpdate,
                                onOpenPlayStore = onOpenPlayStore,
                                onExitApp = onExitApp
                            )
                        }

                        is SplashUpdateUiState.OptionalUpdatePrompt -> {
                            SplashOptionalUpdateCard(
                                message = state.message,
                                onUpdateNow = onRetryUpdate,
                                onContinue = onDismissOptionalUpdate
                            )
                        }

                        SplashUpdateUiState.AllowedToProceed -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashStatusIndicator(
    statusText: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(top = 4.dp)
            .testTag("splash_update_status")
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = BrandAmberDark
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = BrandSlateMuted
        )
    }
}

@Composable
private fun SplashDownloadProgressSection(
    statusMessage: String,
    bytesDownloaded: Long,
    totalBytesToDownload: Long
) {
    val hasDeterminateProgress = totalBytesToDownload > 0L && bytesDownloaded >= 0L
    val fraction = if (hasDeterminateProgress) {
        (bytesDownloaded.toFloat() / totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .testTag("splash_update_progress")
    ) {
        if (hasDeterminateProgress) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandAmberDark,
                trackColor = Color(0xFFF1F5F9)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$statusMessage (${(fraction * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = BrandSlateMuted,
                textAlign = TextAlign.Center
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandAmberDark,
                trackColor = Color(0xFFF1F5F9)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = BrandSlateMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SplashRestartRequiredCard(
    message: String,
    onCompleteUpdate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .testTag("splash_restart_required_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "Update Downloaded",
                tint = BrandAmberDark,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Update Ready to Install",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = BrandSlateDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = BrandSlateMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCompleteUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAmberDark,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("splash_complete_update_button")
            ) {
                Text(
                    text = "Install & Restart",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SplashForceUpdateBlockedCard(
    state: SplashUpdateUiState.UpdateRequiredBlocked,
    onRetryUpdate: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onExitApp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .testTag("splash_force_update_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "Mandatory Update Required",
                tint = BrandAmberDark,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = BrandSlateDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_force_update_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp
                ),
                color = BrandSlateMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_force_update_message")
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (state.canRetryInApp) {
                Button(
                    onClick = onRetryUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAmberDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("splash_retry_update_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Now",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.showOpenPlayStore) {
                OutlinedButton(
                    onClick = onOpenPlayStore,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandSlateDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("splash_open_play_store_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Google Play",
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            TextButton(
                onClick = onExitApp,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = BrandSlateMuted
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("splash_exit_app_button")
            ) {
                Text(
                    text = "Exit App",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SplashOptionalUpdateCard(
    message: String,
    onUpdateNow: () -> Unit,
    onContinue: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .testTag("splash_optional_update_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = BrandSlateDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = BrandSlateMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onContinue,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("splash_skip_optional_update_button")
                ) {
                    Text("Later")
                }
                Button(
                    onClick = onUpdateNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAmberDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("splash_accept_optional_update_button")
                ) {
                    Text("Update")
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
