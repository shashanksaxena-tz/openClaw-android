package com.openclaw.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Design-system colors ──────────────────────────────────────────────
private val TrueBlack = Color(0xFF050508)
private val DarkBg = Color(0xFF0F0F14)
private val ElectricViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val HotPink = Color(0xFFEC4899)
private val VioletGlow = Color(0x40A855F7)
private val CyanGlow = Color(0x3022D3EE)

// ── Particle data ─────────────────────────────────────────────────────
private data class Particle(
    val x: Float,          // normalised 0..1
    val startY: Float,     // normalised 0..1 (bottom-biased)
    val radius: Float,     // dp-ish size
    val speed: Float,      // how fast it rises per second
    val color: Color,
    val maxAlpha: Float,
    val phase: Float,      // random phase for shimmer
)

private fun generateParticles(count: Int): List<Particle> {
    val colors = listOf(
        ElectricViolet, NeonCyan, HotPink,
        Color.White, ElectricViolet.copy(alpha = 0.7f),
    )
    return List(count) {
        Particle(
            x = Random.nextFloat(),
            startY = 0.6f + Random.nextFloat() * 0.5f, // start in lower half
            radius = 1f + Random.nextFloat() * 2.5f,
            speed = 0.06f + Random.nextFloat() * 0.12f,
            color = colors.random(),
            maxAlpha = 0.15f + Random.nextFloat() * 0.55f,
            phase = Random.nextFloat() * 6.2832f,
        )
    }
}

// ── Main splash composable ────────────────────────────────────────────
@Composable
fun SplashScreen(onFinished: () -> Unit) {

    // ── Animation state ───────────────────────────────────────────────
    val bgAlpha = remember { Animatable(0f) }
    val clawProgress = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleSlide = remember { Animatable(24f) } // dp offset
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleSlide = remember { Animatable(16f) }
    val particleTime = remember { Animatable(0f) }
    val orbPulse = remember { Animatable(0f) }

    // ── Orchestrate the animation sequence ────────────────────────────
    LaunchedEffect(Unit) {
        // 1. Background fade (0 → 300 ms)
        launch { bgAlpha.animateTo(1f, tween(400, easing = EaseInOut)) }

        // Particles start moving immediately and run for the whole duration
        launch { particleTime.animateTo(2.5f, tween(2500, easing = LinearEasing)) }

        // Central orb pulse runs continuously
        launch {
            delay(200)
            orbPulse.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }

        // 2. Claw path draw (200 → 1000 ms)
        delay(200)
        launch { clawProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

        // 3. Glow bloom (400 → 900 ms)
        delay(200)
        launch { glowAlpha.animateTo(1f, tween(500, easing = EaseOut)) }

        // 4. Title fade + slide (700 ms)
        delay(300)
        launch { titleAlpha.animateTo(1f, tween(500, easing = EaseOut)) }
        launch { titleSlide.animateTo(0f, tween(500, easing = EaseOut)) }

        // 5. Subtitle fade + slide (900 ms)
        delay(200)
        launch { subtitleAlpha.animateTo(1f, tween(400, easing = EaseOut)) }
        launch { subtitleSlide.animateTo(0f, tween(400, easing = EaseOut)) }

        // 6. Hold, then finish
        delay(800)
        onFinished()
    }

    val particles = remember { generateParticles(40) }
    val density = LocalDensity.current

    // ── Render ────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
        contentAlignment = Alignment.Center,
    ) {

        // Animated background gradient layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha.value),
        ) {
            // Radial gradient from center: dark-bg core with subtle violet edges
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkBg,
                        DarkBg,
                        TrueBlack,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.maxDimension * 0.7f,
                ),
            )

            // Subtle vignette ring of violet at the periphery
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        VioletGlow.copy(alpha = 0.08f * bgAlpha.value),
                    ),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.maxDimension * 0.9f,
                ),
                radius = size.maxDimension,
            )
        }

        // ── Particle / sparkle layer ──────────────────────────────────
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val t = particleTime.value
            for (p in particles) {
                val travel = t * p.speed
                val currentY = p.startY - travel
                if (currentY < -0.05f) continue

                val shimmer = (sin(t * 4f + p.phase).toFloat() + 1f) / 2f
                val fadeIn = (t * 3f).coerceIn(0f, 1f)
                val fadeOut = if (currentY < 0.1f) currentY / 0.1f else 1f
                val alpha = p.maxAlpha * shimmer * fadeIn * fadeOut

                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.radius * density.density,
                    center = Offset(
                        x = p.x * size.width + sin(t * 2f + p.phase).toFloat() * 6f * density.density,
                        y = currentY * size.height,
                    ),
                )
                // Tiny outer glow for bigger particles
                if (p.radius > 2f) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha * 0.3f),
                        radius = p.radius * density.density * 2.5f,
                        center = Offset(
                            x = p.x * size.width + sin(t * 2f + p.phase).toFloat() * 6f * density.density,
                            y = currentY * size.height,
                        ),
                    )
                }
            }
        }

        // ── Central column: logo + text ───────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Claw logo canvas ──────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp),
            ) {
                Canvas(
                    modifier = Modifier.size(160.dp),
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val progress = clawProgress.value
                    val glow = glowAlpha.value
                    val pulse = orbPulse.value

                    // ── Outer glow rings ──────────────────────────────
                    if (glow > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ElectricViolet.copy(alpha = 0.18f * glow),
                                    ElectricViolet.copy(alpha = 0.06f * glow),
                                    Color.Transparent,
                                ),
                                center = Offset(cx, cy),
                                radius = cx * 1.1f,
                            ),
                            radius = cx * 1.1f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonCyan.copy(alpha = 0.08f * glow),
                                    Color.Transparent,
                                ),
                                center = Offset(cx, cy),
                                radius = cx * 1.4f,
                            ),
                            radius = cx * 1.4f,
                        )
                    }

                    // ── Central orb ───────────────────────────────────
                    if (pulse > 0f) {
                        // Core orb
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.9f * pulse),
                                    ElectricViolet.copy(alpha = 0.6f * pulse),
                                    NeonCyan.copy(alpha = 0.2f * pulse),
                                    Color.Transparent,
                                ),
                                center = Offset(cx, cy),
                                radius = 14f * pulse,
                            ),
                            radius = 14f * pulse,
                        )
                        // Breathing glow ring
                        val breathe = 0.8f + 0.2f * sin(particleTime.value * 5f).toFloat()
                        drawCircle(
                            color = ElectricViolet.copy(alpha = 0.12f * pulse * breathe),
                            radius = 26f * pulse,
                            center = Offset(cx, cy),
                        )
                    }

                    // ── Three claw talons ─────────────────────────────
                    drawClawTalons(
                        cx = cx,
                        cy = cy,
                        progress = progress,
                        glow = glow,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── App title ─────────────────────────────────────────────
            Text(
                text = "OpenClaw",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp,
                color = Color.White,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .offset {
                        IntOffset(0, (titleSlide.value * density.density).toInt())
                    },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Subtitle ──────────────────────────────────────────────
            Text(
                text = "AI Assistant",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 4.sp,
                color = NeonCyan.copy(alpha = 0.85f),
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .offset {
                        IntOffset(0, (subtitleSlide.value * density.density).toInt())
                    },
            )
        }
    }
}

// ── Claw drawing helper ───────────────────────────────────────────────
private fun DrawScope.drawClawTalons(
    cx: Float,
    cy: Float,
    progress: Float,
    glow: Float,
) {
    // Three talons evenly spaced at 120-degree intervals, starting upward
    val angles = listOf(-90f, 30f, 150f)
    val colors = listOf(ElectricViolet, NeonCyan, HotPink)
    val glowColors = listOf(
        ElectricViolet.copy(alpha = 0.35f),
        NeonCyan.copy(alpha = 0.30f),
        HotPink.copy(alpha = 0.30f),
    )

    val innerRadius = 18f
    val outerRadius = cx * 0.78f

    for (i in angles.indices) {
        val angleRad = Math.toRadians(angles[i].toDouble())

        // Start point: just outside the central orb
        val sx = cx + innerRadius * cos(angleRad).toFloat()
        val sy = cy + innerRadius * sin(angleRad).toFloat()

        // End point: tip of the talon
        val ex = cx + outerRadius * cos(angleRad).toFloat()
        val ey = cy + outerRadius * sin(angleRad).toFloat()

        // Control points to create an organic curve (swept outward)
        val perpAngle = angleRad + Math.PI / 2.0
        val sweep = outerRadius * 0.35f

        val cp1x = cx + (innerRadius + outerRadius * 0.3f) * cos(angleRad).toFloat() +
                sweep * 0.5f * cos(perpAngle).toFloat()
        val cp1y = cy + (innerRadius + outerRadius * 0.3f) * sin(angleRad).toFloat() +
                sweep * 0.5f * sin(perpAngle).toFloat()

        val cp2x = cx + (innerRadius + outerRadius * 0.7f) * cos(angleRad).toFloat() -
                sweep * 0.3f * cos(perpAngle).toFloat()
        val cp2y = cy + (innerRadius + outerRadius * 0.7f) * sin(angleRad).toFloat() -
                sweep * 0.3f * sin(perpAngle).toFloat()

        val fullPath = Path().apply {
            moveTo(sx, sy)
            cubicTo(cp1x, cp1y, cp2x, cp2y, ex, ey)
        }

        // Measure the path and create a trimmed version based on progress
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(fullPath, false)
        val pathLength = pathMeasure.length

        val trimmedPath = Path()
        if (progress > 0f) {
            pathMeasure.getSegment(0f, pathLength * progress, trimmedPath, true)
        }

        // Glow layer (thicker, more transparent)
        if (glow > 0f && progress > 0f) {
            drawPath(
                path = trimmedPath,
                color = glowColors[i].copy(alpha = glowColors[i].alpha * glow),
                style = Stroke(
                    width = 10f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // Main stroke
        if (progress > 0f) {
            drawPath(
                path = trimmedPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors[i].copy(alpha = 0.7f),
                        colors[i],
                        Color.White.copy(alpha = 0.9f),
                    ),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                ),
                style = Stroke(
                    width = 3.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // Tip sparkle at the leading edge of the drawing stroke
        if (progress > 0.05f && progress < 1f) {
            // Approximate tip position using the trimmed path end
            val tipProgress = progress.coerceIn(0f, 1f)
            val tipX = cx + (innerRadius + (outerRadius - innerRadius) * tipProgress) *
                    cos(angleRad).toFloat()
            val tipY = cy + (innerRadius + (outerRadius - innerRadius) * tipProgress) *
                    sin(angleRad).toFloat()

            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 4f,
                center = Offset(tipX, tipY),
            )
            drawCircle(
                color = colors[i].copy(alpha = 0.4f),
                radius = 10f,
                center = Offset(tipX, tipY),
            )
        }

        // Decorative branching line (smaller, from midpoint outward)
        if (progress > 0.5f) {
            val branchProgress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val branchAngleRad = angleRad + Math.PI / 4.0 * (if (i % 2 == 0) 1 else -1)
            val midX = cx + outerRadius * 0.5f * cos(angleRad).toFloat()
            val midY = cy + outerRadius * 0.5f * sin(angleRad).toFloat()
            val branchLen = outerRadius * 0.3f * branchProgress
            val branchEndX = midX + branchLen * cos(branchAngleRad).toFloat()
            val branchEndY = midY + branchLen * sin(branchAngleRad).toFloat()

            val branchPath = Path().apply {
                moveTo(midX, midY)
                lineTo(branchEndX, branchEndY)
            }
            drawPath(
                path = branchPath,
                color = colors[i].copy(alpha = 0.4f * branchProgress * glow),
                style = Stroke(
                    width = 1.5f,
                    cap = StrokeCap.Round,
                ),
            )
        }
    }

    // ── Connecting arc fragments between talons ───────────────────────
    if (progress > 0.3f) {
        val arcProgress = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val arcRadius = innerRadius + 10f

        for (i in angles.indices) {
            val startAngle = angles[i]
            val endAngle = angles[(i + 1) % angles.size]
            val sweepAngle = ((endAngle - startAngle + 360f) % 360f).let {
                if (it > 180f) it - 360f else it
            }

            val arcPath = Path().apply {
                val startRad = Math.toRadians(startAngle.toDouble())
                val midAngleRad = Math.toRadians((startAngle + sweepAngle * 0.5f).toDouble())
                val endRad = Math.toRadians(endAngle.toDouble())

                moveTo(
                    cx + arcRadius * cos(startRad).toFloat(),
                    cy + arcRadius * sin(startRad).toFloat(),
                )
                quadraticTo(
                    cx + arcRadius * 1.2f * cos(midAngleRad).toFloat(),
                    cy + arcRadius * 1.2f * sin(midAngleRad).toFloat(),
                    cx + arcRadius * cos(endRad).toFloat(),
                    cy + arcRadius * sin(endRad).toFloat(),
                )
            }

            val arcMeasure = PathMeasure()
            arcMeasure.setPath(arcPath, false)
            val trimmedArc = Path()
            arcMeasure.getSegment(0f, arcMeasure.length * arcProgress, trimmedArc, true)

            drawPath(
                path = trimmedArc,
                color = ElectricViolet.copy(alpha = 0.25f * arcProgress * glow),
                style = Stroke(
                    width = 1f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4f, 6f),
                        phase = 0f,
                    ),
                ),
            )
        }
    }
}
