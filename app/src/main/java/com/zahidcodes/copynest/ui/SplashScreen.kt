package com.zahidcodes.copynest.ui

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay

// Brand palette derived from the CopyNest dark navy & gold identity
private val BrandNavyCenter = Color(0xFF07162C)
private val BrandNavyMid = Color(0xFF051122)
private val BrandNavyDeep = Color(0xFF04101E)
private val BrandNavyCardBg = Color(0xFF041228)
private val BrandCopyWhiteBlue = Color(0xFFF0F7FF)
private val BrandNestGold = Color(0xFFFBBF24)
private val BrandNestGoldLight = Color(0xFFFDE68A)
private val BrandNestGoldDeep = Color(0xFFF59E0B)

// Smooth Easing Curves
private val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val SubtleSettleEasing = CubicBezierEasing(0.34f, 1.35f, 0.64f, 1.0f)

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false

        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

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

    var hasNavigated by rememberSaveable { mutableStateOf(false) }
    val timelineMs = remember { Animatable(if (reduceMotion) 1250f else 0f) }

    LaunchedEffect(reduceMotion) {
        if (hasNavigated) {
            onTimeout()
            return@LaunchedEffect
        }

        if (reduceMotion) {
            timelineMs.snapTo(1250f)
            delay(250L)
            if (!hasNavigated) {
                hasNavigated = true
                onTimeout()
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

        if (!hasNavigated) {
            hasNavigated = true
            onTimeout()
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
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandNavyCenter,
                        BrandNavyMid,
                        BrandNavyDeep
                    ),
                    radius = 1100f
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
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
                    color = BrandNavyCardBg,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(142.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    BrandNestGold.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BrandNavyCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_fg_art),
                            contentDescription = "CopyNest Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

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
                                color = BrandCopyWhiteBlue,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append("Copy")
                        }
                        withStyle(
                            style = SpanStyle(
                                color = BrandNestGold,
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
                                        BrandNestGoldLight,
                                        BrandNestGold,
                                        BrandNestGoldDeep
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
