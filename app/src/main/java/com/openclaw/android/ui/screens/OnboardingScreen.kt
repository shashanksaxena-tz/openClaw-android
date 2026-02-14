package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.data.SettingsRepository
import kotlinx.coroutines.launch

// ── Design tokens ────────────────────────────────────────────────────────────
private val DarkBg = Color(0xFF050508)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFEC4899)
private val GlassFill = Color.White.copy(alpha = 0.06f)
private val GlassBorder = Color.White.copy(alpha = 0.12f)
private val SubtleText = Color.White.copy(alpha = 0.55f)
private val BodyText = Color.White.copy(alpha = 0.85f)

private val VioletCyanGradient = Brush.linearGradient(listOf(Violet, Cyan))

// ── Main composable ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settings: SettingsRepository,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Animated background gradient shift
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(8000, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "bgPhase",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // True-black base
                drawRect(DarkBg)
                // Subtle animated gradient orbs
                val cx1 = size.width * (0.2f + 0.3f * gradientPhase)
                val cy1 = size.height * 0.25f
                val cx2 = size.width * (0.8f - 0.3f * gradientPhase)
                val cy2 = size.height * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Violet.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(cx1, cy1),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(cx1, cy1),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(cx2, cy2),
                        radius = size.width * 0.5f,
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(cx2, cy2),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Skip button ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                if (pagerState.currentPage < 2) {
                    TextButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SubtleText,
                        ),
                    ) {
                        Text("Skip", fontSize = 14.sp)
                    }
                }
            }

            // ── Pager ────────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ApiKeyPage(settings)
                    2 -> ReadyPage()
                }
            }

            // ── Bottom navigation ────────────────────────────────────────
            BottomNav(
                pagerState = pagerState,
                hasApiKey = settings.hasAnyApiKey(),
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onComplete = onComplete,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PAGE 1 — Welcome
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomePage() {
    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(2500, easing = EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "logoPulse",
    )
    val logoGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2500, easing = EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "logoGlow",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Animated claw logo ───────────────────────────────────────
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(logoPulse),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val w = size.width
                val h = size.height
                // Glow halo behind the claw
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Violet.copy(alpha = 0.35f * logoGlow),
                            Color.Transparent,
                        ),
                    ),
                    radius = w * 0.7f,
                    center = Offset(w / 2, h / 2),
                )
                // Three curved claw marks
                val clawColors = listOf(Violet, Cyan, Pink)
                val offsets = listOf(-0.28f, 0f, 0.28f)
                for (i in 0..2) {
                    val path = Path().apply {
                        val cx = w / 2 + w * offsets[i]
                        moveTo(cx - w * 0.04f, h * 0.18f)
                        cubicTo(
                            cx - w * 0.10f, h * 0.45f,
                            cx + w * 0.06f, h * 0.60f,
                            cx - w * 0.02f, h * 0.82f,
                        )
                    }
                    drawPath(
                        path = path,
                        color = clawColors[i],
                        style = Stroke(
                            width = w * 0.065f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Title with gradient ──────────────────────────────────────
        Text(
            text = "OpenClaw",
            style = TextStyle(
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                brush = VioletCyanGradient,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your AI, Your Device",
            style = MaterialTheme.typography.titleMedium,
            color = SubtleText,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(32.dp))

        // ── Feature grid (2x3) with staggered entrance ──────────────
        data class Feature(val icon: ImageVector, val label: String)

        val features = listOf(
            Feature(Icons.Default.Folder, "Files"),
            Feature(Icons.Default.Workspaces, "Spaces"),
            Feature(Icons.Default.Mic, "Voice"),
            Feature(Icons.Default.Search, "Search"),
            Feature(Icons.Default.Share, "Share"),
            Feature(Icons.Default.Memory, "Multi-AI"),
        )
        val rows = features.chunked(2)

        rows.forEachIndexed { rowIndex, rowFeatures ->
            val delayMs = 80 * rowIndex
            val rowVisible = remember { mutableStateOf(false) }
            LaunchedEffect(visible) {
                if (visible) {
                    kotlinx.coroutines.delay(delayMs.toLong() + 200L)
                    rowVisible.value = true
                }
            }

            AnimatedVisibility(
                visible = rowVisible.value,
                enter = fadeIn(tween(400)) + slideInVertically(
                    tween(400, easing = EaseOutCubic),
                    initialOffsetY = { it / 3 },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (feature in rowFeatures) {
                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    feature.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = Cyan,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    feature.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BodyText,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PAGE 2 — API Key
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ApiKeyPage(settings: SettingsRepository) {
    val context = LocalContext.current

    // Per-provider key state
    var geminiKey by remember { mutableStateOf(settings.getGeminiKey()) }
    var groqKey by remember { mutableStateOf(settings.getGroqKey()) }
    var cerebrasKey by remember { mutableStateOf(settings.getCerebrasKey()) }
    var showKey by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf("gemini") }

    // Current key for selected provider
    val currentKey = when (selectedProvider) {
        "gemini" -> geminiKey
        "groq" -> groqKey
        "cerebras" -> cerebrasKey
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))

        // ── Title ────────────────────────────────────────────────────
        Text(
            text = "Connect Your AI",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                brush = VioletCyanGradient,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your API key stays encrypted on-device.\nWe recommend Google Gemini -- it's free!",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtleText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        // ── Provider cards ───────────────────────────────────────────
        data class Provider(
            val id: String,
            val name: String,
            val desc: String,
            val url: String,
        )

        val providers = listOf(
            Provider(
                "gemini",
                "Google Gemini",
                "Free: 250-1500 req/day, vision, 1M context",
                "https://aistudio.google.com/apikey",
            ),
            Provider(
                "groq",
                "Groq",
                "Free: fast inference, open-source models",
                "https://console.groq.com/keys",
            ),
            Provider(
                "cerebras",
                "Cerebras",
                "Free: fastest inference speed",
                "https://cloud.cerebras.ai/",
            ),
        )

        for (provider in providers) {
            val isSelected = selectedProvider == provider.id

            // Animated neon border
            val borderAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(300),
                label = "border_${provider.id}",
            )

            val neonBorder = if (isSelected) {
                Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Violet.copy(alpha = borderAlpha),
                            Cyan.copy(alpha = borderAlpha),
                        ),
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
            } else {
                Modifier.border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            // Glow behind selected card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            ) {
                if (isSelected) {
                    // Neon glow layer
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Violet.copy(alpha = 0.12f),
                                            Cyan.copy(alpha = 0.08f),
                                        ),
                                    ),
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                )
                            },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .then(neonBorder)
                        .background(
                            if (isSelected) GlassFill.copy(alpha = 0.10f)
                            else GlassFill,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selectedProvider = provider.id }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Custom radio indicator
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(
                                    width = 2.dp,
                                    brush = if (isSelected) VioletCyanGradient
                                    else Brush.linearGradient(
                                        listOf(SubtleText, SubtleText)
                                    ),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(VioletCyanGradient, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                provider.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else BodyText,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                provider.desc,
                                fontSize = 12.sp,
                                color = SubtleText,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── API key input (glass style) ──────────────────────────────
        OutlinedTextField(
            value = currentKey,
            onValueChange = { key ->
                when (selectedProvider) {
                    "gemini" -> {
                        geminiKey = key
                        settings.setGeminiKey(key)
                    }
                    "groq" -> {
                        groqKey = key
                        settings.setGroqKey(key)
                    }
                    "cerebras" -> {
                        cerebrasKey = key
                        settings.setCerebrasKey(key)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("API Key", color = SubtleText)
            },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle visibility",
                        tint = SubtleText,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = BodyText,
                cursorColor = Violet,
                focusedBorderColor = Violet.copy(alpha = 0.7f),
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassFill,
                unfocusedContainerColor = GlassFill,
                focusedLabelColor = Violet,
                unfocusedLabelColor = SubtleText,
            ),
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(14.dp))

        // ── "Get Free API Key" button ────────────────────────────────
        val url = providers.find { it.id == selectedProvider }?.url
        url?.let {
            GradientButton(
                text = "Get Free API Key",
                gradient = VioletCyanGradient,
                icon = Icons.Default.OpenInNew,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                },
                modifier = Modifier.fillMaxWidth(),
                outlined = true,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PAGE 3 — Ready
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReadyPage() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Animated checkmark draw progress
    val checkProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 200, easing = EaseOutCubic),
        label = "check",
    )

    // Glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "readyGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // ── Animated checkmark ───────────────────────────────────────
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h / 2
                val r = w * 0.42f

                // Glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = 0.25f * glowAlpha),
                            Color.Transparent,
                        ),
                    ),
                    radius = r * 1.8f,
                    center = Offset(cx, cy),
                )

                // Circle outline
                val circleProgress = (checkProgress * 2f).coerceAtMost(1f)
                drawArc(
                    brush = VioletCyanGradient,
                    startAngle = -90f,
                    sweepAngle = 360f * circleProgress,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )

                // Checkmark (draws after circle)
                val tickProgress = ((checkProgress - 0.5f) * 2f).coerceIn(0f, 1f)
                if (tickProgress > 0f) {
                    val path = Path()
                    // Checkmark points
                    val p1 = Offset(cx - r * 0.35f, cy + r * 0.05f)
                    val p2 = Offset(cx - r * 0.05f, cy + r * 0.35f)
                    val p3 = Offset(cx + r * 0.40f, cy - r * 0.25f)

                    if (tickProgress <= 0.5f) {
                        // First stroke of checkmark
                        val t = tickProgress * 2f
                        path.moveTo(p1.x, p1.y)
                        path.lineTo(
                            p1.x + (p2.x - p1.x) * t,
                            p1.y + (p2.y - p1.y) * t,
                        )
                    } else {
                        // Full first stroke + partial second
                        val t = (tickProgress - 0.5f) * 2f
                        path.moveTo(p1.x, p1.y)
                        path.lineTo(p2.x, p2.y)
                        path.lineTo(
                            p2.x + (p3.x - p2.x) * t,
                            p2.y + (p3.y - p2.y) * t,
                        )
                    }
                    drawPath(
                        path = path,
                        brush = VioletCyanGradient,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── "You're Ready" text ──────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, delayMillis = 600)),
        ) {
            Text(
                text = "You're Ready",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    brush = VioletCyanGradient,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 800)),
        ) {
            Text(
                text = "Try these to get started:",
                style = MaterialTheme.typography.bodyLarge,
                color = SubtleText,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Tips in glass cards with staggered entrance ──────────────
        val tips = listOf(
            "\"Help me write a grocery list\"",
            "\"Create a project plan in markdown\"",
            "Share a photo and ask \"What's in this image?\"",
            "Create a Space to organize a project",
        )

        tips.forEachIndexed { index, tip ->
            val tipVisible = remember { mutableStateOf(false) }
            LaunchedEffect(visible) {
                if (visible) {
                    kotlinx.coroutines.delay(900L + index * 120L)
                    tipVisible.value = true
                }
            }

            AnimatedVisibility(
                visible = tipVisible.value,
                enter = fadeIn(tween(400)) + slideInVertically(
                    tween(400, easing = EaseOutCubic),
                    initialOffsetY = { it / 4 },
                ),
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Violet.copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Violet,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tip,
                            fontSize = 14.sp,
                            color = BodyText,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Bottom navigation bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BottomNav(
    pagerState: PagerState,
    hasApiKey: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Back button ──────────────────────────────────────────────
        if (pagerState.currentPage > 0) {
            TextButton(
                onClick = onBack,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = SubtleText),
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        } else {
            Spacer(Modifier.width(80.dp))
        }

        // ── Animated page indicators ─────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val isActive = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = if (isActive) 28.dp else 8.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "dot_$index",
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isActive) Modifier.background(VioletCyanGradient)
                            else Modifier.background(Color.White.copy(alpha = 0.15f))
                        ),
                )
            }
        }

        // ── Next / Get Started button ────────────────────────────────
        when (pagerState.currentPage) {
            2 -> {
                GradientButton(
                    text = "Start Exploring",
                    gradient = Brush.linearGradient(listOf(Violet, Pink)),
                    onClick = onComplete,
                )
            }
            1 -> {
                GradientButton(
                    text = "Next",
                    gradient = VioletCyanGradient,
                    onClick = {
                        if (hasApiKey) onNext()
                    },
                    enabled = hasApiKey,
                )
            }
            else -> {
                GradientButton(
                    text = "Next",
                    gradient = VioletCyanGradient,
                    onClick = onNext,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Reusable components
// ══════════════════════════════════════════════════════════════════════════════

/** A glass-morphism card with translucent fill and subtle border. */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(GlassFill, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** A pill-shaped button with gradient background, or outlined gradient variant. */
@Composable
private fun GradientButton(
    text: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.4f

    if (outlined) {
        Box(
            modifier = modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, gradient, RoundedCornerShape(24.dp))
                .background(GlassFill, RoundedCornerShape(24.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Violet.copy(alpha = alpha),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(brush = gradient),
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    brush = gradient,
                    shape = RoundedCornerShape(22.dp),
                    alpha = alpha,
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = alpha),
                )
            }
        }
    }
}
