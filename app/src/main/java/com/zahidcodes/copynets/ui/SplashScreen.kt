package com.zahidcodes.copynets.ui

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
import com.zahidcodes.copynets.R
import kotlinx.coroutines.delay

// Brand palette derived directly from the CopyNest reference logo
private val BrandNavyCenter = Color(0xFF0A2540)
private val BrandNavyMid = Color(0xFF071B30)
private val BrandNavyDeep = Color(0xFF04101E)
private val BrandNavyCardBg = Color(0xFF06192D)
private val BrandRoyalBlueGlow = Color(0xFF2563EB)
private val BrandCopyWhiteBlue = Color(0xFFF0F7FF)
private val BrandNestGold = Color(0xFFFBBF24)
private val BrandNestGoldLight = Color(0xFFFDE68A)
private val BrandNestGoldDeep = Color(0xFFF59E0B)

// Choreographed Easing Curves
private val SoftEntranceEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private val ApproachDepthEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f)
private val SettleOvershootOutEasing = CubicBezierEasing(0.34f, 1.48f, 0.64f, 1.0f)
private val SettleReturnEasing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f)
private val TypographyRevealEasing = CubicBezierEasing(0.20f, 0.85f, 0.25f, 1.0f)
private val UnderlineRevealEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density

    // Respect system status bar and navigation bar contrast on deep navy splash screen
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

    // Check Android accessibility reduced-motion / animator duration scale setting
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
    val timelineMs = remember { Animatable(if (reduceMotion) 2500f else 0f) }

    LaunchedEffect(reduceMotion) {
        if (hasNavigated) {
            onTimeout()
            return@LaunchedEffect
        }

        if (reduceMotion) {
            timelineMs.snapTo(2500f)
            delay(350L)
            if (!hasNavigated) {
                hasNavigated = true
                onTimeout()
            }
            return@LaunchedEffect
        }

        timelineMs.snapTo(0f)
        timelineMs.animateTo(
            targetValue = 2700f,
            animationSpec = tween(
                durationMillis = 2700,
                easing = LinearEasing
            )
        )

        if (!hasNavigated) {
            hasNavigated = true
            onTimeout()
        }
    }

    // --- 5-Phase Choreographed Timeline (0–2700 ms) ---
    val t = timelineMs.value

    // Phase 1 (0–400 ms): Soft Entrance — Logo is immediately visible at center (0.38x -> 0.48x, alpha 0.85 -> 0.95)
    // so there is NEVER a blank blue-only screen when the app opens.
    // Phase 2 (400–1200 ms): Logo Approaches (0.48x -> 1.00x, alpha 0.95 -> 1.00)
    // Phase 3 (1200–1700 ms): Icon Settles (subtle overshoot to 1.04x, smooth return to 1.00x)
    val iconScale = when {
        t <= 400f -> {
            val p = SoftEntranceEasing.transform((t / 400f).coerceIn(0f, 1f))
            lerpFloat(0.38f, 0.48f, p)
        }
        t <= 1200f -> {
            val p = ApproachDepthEasing.transform(((t - 400f) / 800f).coerceIn(0f, 1f))
            lerpFloat(0.48f, 1.00f, p)
        }
        t <= 1430f -> {
            val p = SettleOvershootOutEasing.transform(((t - 1200f) / 230f).coerceIn(0f, 1f))
            lerpFloat(1.00f, 1.04f, p)
        }
        t <= 1700f -> {
            val p = SettleReturnEasing.transform(((t - 1430f) / 270f).coerceIn(0f, 1f))
            lerpFloat(1.04f, 1.00f, p)
        }
        else -> 1.00f
    }

    val iconAlpha = when {
        t <= 400f -> {
            val p = SoftEntranceEasing.transform((t / 400f).coerceIn(0f, 1f))
            lerpFloat(0.85f, 0.95f, p)
        }
        t <= 800f -> {
            val p = ApproachDepthEasing.transform(((t - 400f) / 400f).coerceIn(0f, 1f))
            lerpFloat(0.95f, 1.0f, p)
        }
        else -> 1.0f
    }

    val iconTranslationYDp = when {
        t <= 400f -> {
            val p = SoftEntranceEasing.transform((t / 400f).coerceIn(0f, 1f))
            lerpFloat(10f, 6f, p)
        }
        t <= 1200f -> {
            val p = ApproachDepthEasing.transform(((t - 400f) / 800f).coerceIn(0f, 1f))
            lerpFloat(6f, 0f, p)
        }
        else -> 0f
    }

    // Coordinated highlight & depth progression during Phase 2 (400–1200 ms)
    val goldenRibbonProgress = ApproachDepthEasing.transform(((t - 450f) / 750f).coerceIn(0f, 1f))

    // Phase 4 — CopyNest Typography Reveal (1500–2200 ms)
    val wordmarkProgress = TypographyRevealEasing.transform(((t - 1500f) / 520f).coerceIn(0f, 1f))
    val wordmarkAlpha = wordmarkProgress
    val wordmarkScale = lerpFloat(0.88f, 1.00f, wordmarkProgress)
    val wordmarkTranslationYDp = lerpFloat(14f, 0f, wordmarkProgress)

    // Golden underline left-to-right animation (1700–2200 ms)
    val underlineProgress = UnderlineRevealEasing.transform(((t - 1700f) / 500f).coerceIn(0f, 1f))

    // Phase 5 — Final Brand Hold & Subtle Golden Light Sweep (2200–2700 ms)
    val lightSweepProgress = ((t - 2200f) / 420f).coerceIn(0f, 1f)
    val lightSweepAlpha = if (lightSweepProgress <= 0f || lightSweepProgress >= 1f) {
        0f
    } else {
        kotlin.math.sin(lightSweepProgress * Math.PI.toFloat()) * 0.30f
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
                    radius = 1200f
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
            // Choreographed Clipboard Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(176.dp)
                    .graphicsLayer(
                        scaleX = iconScale,
                        scaleY = iconScale,
                        alpha = iconAlpha,
                        translationY = iconTranslationYDp * density,
                        cameraDistance = 14f * density
                    )
            ) {
                // Soft royal-blue ambient depth glow behind the approaching clipboard icon
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val glowStrength = (0.4f + 0.6f * ((t / 1200f).coerceIn(0f, 1f)))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BrandRoyalBlueGlow.copy(alpha = 0.28f * glowStrength),
                                BrandNavyCenter.copy(alpha = 0.12f * glowStrength),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.minDimension * 0.55f
                        ),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                }

                // Reference CopyNest Logo Asset Container
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = BrandNavyCardBg,
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .size(148.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    BrandNestGold.copy(alpha = 0.35f),
                                    BrandRoyalBlueGlow.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "CopyNest Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                                .testTag("splash_logo_image")
                        )

                        // Phase 2 & Phase 5 subtle golden highlight sweep across the clipboard & ribbon
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (goldenRibbonProgress in 0.02f..0.98f) {
                                val sweepX = size.width * goldenRibbonProgress
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            BrandNestGoldLight.copy(alpha = 0.18f * (1f - goldenRibbonProgress)),
                                            Color.Transparent
                                        ),
                                        startX = (sweepX - size.width * 0.28f).coerceAtLeast(0f),
                                        endX = (sweepX + size.width * 0.28f).coerceAtMost(size.width)
                                    )
                                )
                            }

                            if (lightSweepAlpha > 0f) {
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

            // Phase 4 — CopyNest Typography & Golden Underline Reveal (1500–2200 ms)
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
                        fontSize = 31.sp,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_wordmark_text")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Golden Underline animated from left to right
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(4.dp)
                        .testTag("splash_golden_underline"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (underlineProgress > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(underlineProgress.coerceIn(0f, 1f))
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
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
