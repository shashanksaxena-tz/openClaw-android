package com.openclaw.android.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.openclaw.android.agent.AgentEvent
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.AgentState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

// --- Design tokens ---
private val DarkBg = Color(0xFF050508)
private val ElectricViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val HotPink = Color(0xFFEC4899)
private val DeepViolet = Color(0xFF2E1065)
private val DeepCyan = Color(0xFF083344)
private val DeepPink = Color(0xFF500724)
private val GlassWhite = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x33FFFFFF)
private val MutedRed = Color(0xFFEF4444)

// --- VoiceParticle data ---
private data class VoiceParticle(
    val x: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val phaseOffset: Float,
)

/**
 * Full-screen voice conversation mode. Like a phone call with the AI.
 * Flow: Listen -> Send to LLM -> TTS response -> Auto-listen again
 *
 * Immersive dark design with animated gradient background,
 * floating particles, and state-driven orb visualizations.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceConversationScreen(
    runtime: AgentRuntime,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val agentState by runtime.state.collectAsState()
    val events by runtime.events.collectAsState()

    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var transcribedText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var autoListen by remember { mutableStateOf(true) }

    // TTS
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        engine
    }

    // Speech recognizer
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    // Watch for assistant responses to speak them
    LaunchedEffect(events.size) {
        val lastEvent = events.lastOrNull()
        if (lastEvent is AgentEvent.AssistantMessage && voiceState == VoiceState.PROCESSING) {
            responseText = lastEvent.text
            voiceState = VoiceState.SPEAKING
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    // TTS callbacks run on a background thread — dispatch to main for Compose state
                    scope.launch {
                        voiceState = VoiceState.IDLE
                        // Auto-listen after speaking
                        if (autoListen && micPermission.status.isGranted && speechRecognizer != null) {
                            delay(500)
                            startListening(speechRecognizer, recognizerIntent)
                            voiceState = VoiceState.LISTENING
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    scope.launch {
                        voiceState = VoiceState.IDLE
                    }
                }
            })
            // Strip markdown for cleaner TTS
            val cleanText = lastEvent.text
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\*(.+?)\\*"), "$1")
                .replace(Regex("`[^`]+`"), "")
                .replace(Regex("```[\\s\\S]*?```"), "")
                .replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
                .trim()
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "voice-conv")
        }
    }

    // Track agent state
    LaunchedEffect(agentState) {
        if (agentState is AgentState.Running && voiceState != VoiceState.SPEAKING) {
            voiceState = VoiceState.PROCESSING
        }
    }

    // Setup speech recognizer listener
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Error 7 = no speech detected, auto-restart
                if (error == SpeechRecognizer.ERROR_NO_MATCH && autoListen && voiceState == VoiceState.LISTENING) {
                    scope.launch {
                        delay(300)
                        if (voiceState == VoiceState.LISTENING) {
                            startListening(speechRecognizer, recognizerIntent)
                        }
                    }
                } else {
                    voiceState = VoiceState.IDLE
                    transcribedText = ""
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    transcribedText = text
                    voiceState = VoiceState.PROCESSING
                    scope.launch {
                        runtime.sendMessage(text)
                    }
                } else {
                    voiceState = VoiceState.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                transcribedText = matches?.firstOrNull() ?: ""
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!micPermission.status.isGranted) {
            micPermission.launchPermissionRequest()
        }
    }

    // =========================================================================
    // ANIMATIONS
    // =========================================================================

    val infiniteTransition = rememberInfiniteTransition(label = "voiceScreen")

    // --- Animated background gradient ---
    val bgPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bgPhase",
    )

    // Cycle through three deep color stops
    val bgColor1 = lerpThreeColors(DeepViolet, DeepCyan, DeepPink, bgPhase)
    val bgColor2 = lerpThreeColors(DeepCyan, DeepPink, DeepViolet, bgPhase)
    val bgColor3 = lerpThreeColors(DeepPink, DeepViolet, DeepCyan, bgPhase)

    // --- VoiceParticles ---
    val particles = remember {
        List(30) {
            VoiceParticle(
                x = Random.nextFloat(),
                speed = 0.3f + Random.nextFloat() * 0.7f,
                size = 1.5f + Random.nextFloat() * 3f,
                alpha = 0.15f + Random.nextFloat() * 0.35f,
                phaseOffset = Random.nextFloat(),
            )
        }
    }

    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleTime",
    )

    // --- Orb: IDLE breathing ---
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleBreath",
    )

    val idleGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleGlow",
    )

    // --- Orb: LISTENING sonar rings ---
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(400),
        ),
        label = "ring2",
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(800),
        ),
        label = "ring3",
    )

    val listeningPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "listeningPulse",
    )

    // --- Orb: PROCESSING orbital rotation ---
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitalAngle",
    )

    val processingPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processingPulse",
    )

    // --- Orb: SPEAKING wave ---
    val speakingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "speakingPhase",
    )

    // --- Auto-listen icon rotation ---
    val autoListenRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "autoListenSpin",
    )

    // Animated state accent color
    val stateColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> ElectricViolet.copy(alpha = 0.6f)
            VoiceState.LISTENING -> HotPink
            VoiceState.PROCESSING -> NeonCyan
            VoiceState.SPEAKING -> ElectricViolet
        },
        animationSpec = tween(durationMillis = 400),
        label = "stateColor",
    )

    // =========================================================================
    // UI
    // =========================================================================

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        // --- Animated gradient background layer ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(bgColor1, bgColor2, bgColor3),
                ),
                alpha = 0.45f,
            )
        }

        // --- VoiceParticle layer ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVoiceParticles(particles, particleTime, stateColor)
        }

        // --- Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ===================== TOP BAR =====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // Close button - left
                IconButton(
                    onClick = {
                        tts?.stop()
                        speechRecognizer?.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }

                // Title - center
                Text(
                    text = "Voice Mode",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center),
                )

                // Auto-listen toggle - right
                IconButton(
                    onClick = { autoListen = !autoListen },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        if (autoListen) Icons.Default.Loop else Icons.Default.StopCircle,
                        contentDescription = if (autoListen) "Auto-listen on" else "Auto-listen off",
                        tint = if (autoListen) NeonCyan else Color.White.copy(alpha = 0.4f),
                        modifier = if (autoListen) Modifier.rotate(autoListenRotation) else Modifier,
                    )
                }
            }

            // ===================== MIDDLE: ORB + STATUS + TEXT =====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // --- Animated orb ---
                val orbSizeDp = 200.dp

                Box(
                    modifier = Modifier.size(orbSizeDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension / 2f * 0.4f

                        when (voiceState) {
                            // ---------- IDLE: soft breathing orb ----------
                            VoiceState.IDLE -> {
                                val r = baseRadius * idleBreath
                                // Outer glow
                                drawCircle(
                                    color = ElectricViolet.copy(alpha = idleGlow * 0.3f),
                                    radius = r * 1.8f,
                                    center = center,
                                )
                                // Mid layer
                                drawCircle(
                                    color = ElectricViolet.copy(alpha = idleGlow * 0.5f),
                                    radius = r * 1.3f,
                                    center = center,
                                )
                                // Core
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ElectricViolet.copy(alpha = 0.7f),
                                            ElectricViolet.copy(alpha = 0.2f),
                                        ),
                                        center = center,
                                        radius = r,
                                    ),
                                    radius = r,
                                    center = center,
                                )
                            }

                            // ---------- LISTENING: sonar/radar rings ----------
                            VoiceState.LISTENING -> {
                                val coreR = baseRadius * listeningPulse
                                // Core
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            HotPink.copy(alpha = 0.8f),
                                            HotPink.copy(alpha = 0.25f),
                                        ),
                                        center = center,
                                        radius = coreR,
                                    ),
                                    radius = coreR,
                                    center = center,
                                )

                                // 3 expanding rings
                                val maxRingRadius = size.minDimension / 2f * 0.95f
                                for ((i, ringProgress) in listOf(ring1, ring2, ring3).withIndex()) {
                                    val ringR = coreR + (maxRingRadius - coreR) * ringProgress
                                    val ringAlpha = (1f - ringProgress) * 0.6f
                                    drawCircle(
                                        color = HotPink.copy(alpha = ringAlpha),
                                        radius = ringR,
                                        center = center,
                                        style = Stroke(width = 2.5f - ringProgress * 1.5f),
                                    )
                                }
                            }

                            // ---------- PROCESSING: orbital dots ----------
                            VoiceState.PROCESSING -> {
                                val coreR = baseRadius * 0.7f * processingPulse
                                // Core glow
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            NeonCyan.copy(alpha = 0.5f),
                                            NeonCyan.copy(alpha = 0.1f),
                                        ),
                                        center = center,
                                        radius = coreR * 1.6f,
                                    ),
                                    radius = coreR * 1.6f,
                                    center = center,
                                )
                                // Core
                                drawCircle(
                                    color = NeonCyan.copy(alpha = 0.6f),
                                    radius = coreR,
                                    center = center,
                                )

                                // 5 orbital dots
                                val orbitRadius = baseRadius * 1.4f
                                val dotCount = 5
                                for (i in 0 until dotCount) {
                                    val angleOffset = (360f / dotCount) * i
                                    val angle = Math.toRadians((orbitalAngle + angleOffset).toDouble())
                                    val dotX = center.x + (orbitRadius * cos(angle)).toFloat()
                                    val dotY = center.y + (orbitRadius * sin(angle)).toFloat()
                                    val dotAlpha = 0.5f + 0.5f * sin(angle).toFloat()
                                    val dotSize = 4f + 3f * ((sin(angle).toFloat() + 1f) / 2f)
                                    drawCircle(
                                        color = NeonCyan.copy(alpha = dotAlpha.coerceIn(0.3f, 1f)),
                                        radius = dotSize,
                                        center = Offset(dotX, dotY),
                                    )
                                }

                                // Faint orbit path
                                drawCircle(
                                    color = NeonCyan.copy(alpha = 0.12f),
                                    radius = orbitRadius,
                                    center = center,
                                    style = Stroke(width = 1f),
                                )
                            }

                            // ---------- SPEAKING: wave-pulsing concentric circles ----------
                            VoiceState.SPEAKING -> {
                                val ringCount = 6
                                val maxR = size.minDimension / 2f * 0.9f
                                for (i in 0 until ringCount) {
                                    val fraction = i.toFloat() / ringCount
                                    val waveFactor = 1f + 0.12f * sin(speakingPhase + fraction * 2f * PI.toFloat())
                                    val ringR = (baseRadius * 0.5f + (maxR - baseRadius * 0.5f) * fraction) * waveFactor
                                    val ringAlpha = (1f - fraction) * 0.5f
                                    drawCircle(
                                        color = ElectricViolet.copy(alpha = ringAlpha),
                                        radius = ringR,
                                        center = center,
                                        style = Stroke(width = 2f),
                                    )
                                }
                                // Core
                                val coreWave = 1f + 0.06f * sin(speakingPhase)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ElectricViolet.copy(alpha = 0.8f),
                                            ElectricViolet.copy(alpha = 0.2f),
                                        ),
                                        center = center,
                                        radius = baseRadius * 0.55f * coreWave,
                                    ),
                                    radius = baseRadius * 0.55f * coreWave,
                                    center = center,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // --- Status text ---
                AnimatedContent(
                    targetState = voiceState,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "statusText",
                ) { state ->
                    Text(
                        text = when (state) {
                            VoiceState.LISTENING -> "Listening..."
                            VoiceState.PROCESSING -> "Thinking..."
                            VoiceState.SPEAKING -> "Speaking..."
                            VoiceState.IDLE -> "Tap the mic to start"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                        ),
                        color = stateColor,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // --- Transcript / response text in glass pill ---
                val displayText = when (voiceState) {
                    VoiceState.LISTENING -> transcribedText.ifBlank { "..." }
                    VoiceState.PROCESSING -> transcribedText
                    VoiceState.SPEAKING -> responseText.take(200) + if (responseText.length > 200) "..." else ""
                    VoiceState.IDLE -> ""
                }

                AnimatedVisibility(
                    visible = displayText.isNotBlank(),
                    enter = fadeIn(tween(400)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassWhite)
                            .border(
                                width = 1.dp,
                                color = stateColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ===================== BOTTOM CONTROLS =====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- Secondary action row (stop speaking / end call) ---
                AnimatedVisibility(
                    visible = voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING || voiceState == VoiceState.PROCESSING,
                    enter = fadeIn(tween(250)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Stop speaking
                        if (voiceState == VoiceState.SPEAKING) {
                            GlassPillButton(
                                onClick = {
                                    tts?.stop()
                                    voiceState = VoiceState.IDLE
                                },
                                backgroundColor = MutedRed.copy(alpha = 0.15f),
                                borderColor = MutedRed.copy(alpha = 0.4f),
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MutedRed,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Stop",
                                    color = MutedRed,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }

                            Spacer(Modifier.width(16.dp))
                        }

                        // End call
                        GlassPillButton(
                            onClick = {
                                tts?.stop()
                                speechRecognizer?.cancel()
                                runtime.cancel()
                                voiceState = VoiceState.IDLE
                            },
                            backgroundColor = Color.Red.copy(alpha = 0.12f),
                            borderColor = Color.Red.copy(alpha = 0.35f),
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MutedRed,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "End",
                                color = MutedRed,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // --- Main mic FAB ---
                val fabContainerColor by animateColorAsState(
                    targetValue = when (voiceState) {
                        VoiceState.LISTENING -> MutedRed
                        else -> Color.Transparent // We draw our own gradient
                    },
                    animationSpec = tween(300),
                    label = "fabColor",
                )

                Box(contentAlignment = Alignment.Center) {
                    // Glow underneath the FAB
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val glowColor = when (voiceState) {
                            VoiceState.LISTENING -> HotPink.copy(alpha = 0.4f)
                            VoiceState.PROCESSING -> NeonCyan.copy(alpha = 0.3f)
                            VoiceState.SPEAKING -> ElectricViolet.copy(alpha = 0.3f)
                            VoiceState.IDLE -> ElectricViolet.copy(alpha = 0.2f)
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor, Color.Transparent),
                                center = center,
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                            center = center,
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            when (voiceState) {
                                VoiceState.IDLE -> {
                                    if (micPermission.status.isGranted && speechRecognizer != null) {
                                        voiceState = VoiceState.LISTENING
                                        transcribedText = ""
                                        startListening(speechRecognizer, recognizerIntent)
                                    } else {
                                        micPermission.launchPermissionRequest()
                                    }
                                }
                                VoiceState.LISTENING -> {
                                    speechRecognizer?.stopListening()
                                    voiceState = VoiceState.IDLE
                                }
                                VoiceState.SPEAKING -> {
                                    tts?.stop()
                                    voiceState = VoiceState.IDLE
                                }
                                VoiceState.PROCESSING -> {
                                    runtime.cancel()
                                    voiceState = VoiceState.IDLE
                                }
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .then(
                                if (voiceState != VoiceState.LISTENING) {
                                    Modifier.drawBehind {
                                        drawCircle(
                                            brush = Brush.linearGradient(
                                                colors = listOf(ElectricViolet, HotPink),
                                            ),
                                            radius = size.minDimension / 2f,
                                        )
                                    }
                                } else Modifier
                            ),
                        shape = CircleShape,
                        containerColor = fabContainerColor,
                        contentColor = Color.White,
                    ) {
                        Icon(
                            when (voiceState) {
                                VoiceState.LISTENING -> Icons.Default.Stop
                                VoiceState.PROCESSING -> Icons.Default.Close
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Voice",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// HELPER COMPOSABLES
// =============================================================================

/**
 * Glass-morphism pill-shaped button used for secondary actions.
 */
@Composable
private fun GlassPillButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Lerp across three colors in a cycle: A -> B -> C -> A, based on fraction [0..1).
 */
private fun lerpThreeColors(a: Color, b: Color, c: Color, fraction: Float): Color {
    return when {
        fraction < 1f / 3f -> {
            val t = fraction * 3f
            lerp(a, b, t)
        }
        fraction < 2f / 3f -> {
            val t = (fraction - 1f / 3f) * 3f
            lerp(b, c, t)
        }
        else -> {
            val t = (fraction - 2f / 3f) * 3f
            lerp(c, a, t)
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

/**
 * Draw small glowing dots that float upward in the background.
 */
private fun DrawScope.drawVoiceParticles(
    particles: List<VoiceParticle>,
    time: Float,
    accentColor: Color,
) {
    for (p in particles) {
        val progress = (time * p.speed + p.phaseOffset) % 1f
        val x = p.x * size.width + sin((progress + p.phaseOffset) * 2.0 * PI).toFloat() * 20f
        val y = size.height * (1f - progress)
        val alpha = p.alpha * (1f - (progress - 0.5f).let { it * it } * 4f).coerceIn(0f, 1f)
        drawCircle(
            color = accentColor.copy(alpha = alpha),
            radius = p.size,
            center = Offset(x, y),
        )
    }
}

private fun startListening(recognizer: SpeechRecognizer, intent: Intent) {
    try {
        recognizer.startListening(intent)
    } catch (_: Exception) {
        // Ignore if recognizer is in bad state
    }
}
