package com.openclaw.android.ui.components

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale

// ── Design-system palette ──────────────────────────────────────────────────────
private val ElectricViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val HotPink = Color(0xFFEC4899)
private val GlassBg = Color.White.copy(alpha = 0.08f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)
private val MutedGlass = Color.White.copy(alpha = 0.04f)
private val MutedTint = Color.White.copy(alpha = 0.25f)

/**
 * Animated voice input button. Hold to record, release to send.
 * Requests RECORD_AUDIO permission before starting recognition.
 *
 * Visual design: dark-first glass morphism with pulsing violet/cyan rings
 * while listening, color-shifting button, and floating partial-result pill.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceInputButton(
    onResult: (String) -> Unit,
    enabled: Boolean = true,
    onListeningChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var isListening by remember { mutableStateOf(false) }
    var partialResult by remember { mutableStateOf("") }

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

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                onListeningChanged(false)
            }
            override fun onError(error: Int) {
                isListening = false
                onListeningChanged(false)
                partialResult = ""
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    onResult(text)
                }
                partialResult = ""
                isListening = false
                onListeningChanged(false)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partialResult = matches?.firstOrNull() ?: ""
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    // ── Animations ─────────────────────────────────────────────────────────────

    // Smooth scale: idle ↔ listening via spring
    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "buttonScale",
    )

    // Infinite transition drives rings + color shift while listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic")

    // Ring 1 progress (0 → 1, loops)
    val ring1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )

    // Ring 2 – offset by ~33%
    val ring2Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(467),
        ),
        label = "ring2",
    )

    // Ring 3 – offset by ~66%
    val ring3Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(933),
        ),
        label = "ring3",
    )

    // Button color: pulses violet ↔ hot pink while listening
    val pulseColor by infiniteTransition.animateColor(
        initialValue = ElectricViolet,
        targetValue = HotPink,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseColor",
    )

    val canRecord = micPermission.status.isGranted && speechRecognizer != null

    // ── Layout ─────────────────────────────────────────────────────────────────

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Partial result floating pill (shown above the button)
        AnimatedVisibility(
            visible = isListening && partialResult.isNotBlank(),
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            Text(
                text = partialResult,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .background(
                        color = GlassBg,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Ring canvas + mic button layered in a Box
        Box(
            contentAlignment = Alignment.Center,
            // Canvas needs room for the largest ring expansion (~2.4x button radius)
            modifier = Modifier.size(110.dp),
        ) {
            // ── Expanding rings (Canvas) ───────────────────────────────────────
            if (isListening) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = 22.dp.toPx() // matches button radius (44dp / 2)
                    val maxExpand = 32.dp.toPx()   // rings expand up to this much beyond base

                    // Helper: draw one ring at a given progress (0..1)
                    fun drawRing(progress: Float) {
                        val radius = baseRadius + maxExpand * progress
                        val alpha = (1f - progress).coerceIn(0f, 0.6f)
                        // Lerp between violet and cyan based on progress
                        val ringColor = lerp(ElectricViolet, NeonCyan, progress).copy(alpha = alpha)
                        drawCircle(
                            color = ringColor,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }

                    drawRing(ring1Progress)
                    drawRing(ring2Progress)
                    drawRing(ring3Progress)
                }
            }

            // ── Mic button ─────────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(
                        brush = when {
                            isListening -> Brush.linearGradient(
                                colors = listOf(pulseColor, pulseColor.copy(alpha = 0.75f)),
                            )
                            !canRecord -> Brush.linearGradient(
                                colors = listOf(MutedGlass, MutedGlass),
                            )
                            else -> Brush.linearGradient(
                                colors = listOf(
                                    ElectricViolet.copy(alpha = 0.55f),
                                    ElectricViolet.copy(alpha = 0.30f),
                                ),
                            )
                        },
                    )
                    .background(
                        // Glass overlay border hint
                        color = if (isListening) Color.Transparent else GlassBorder,
                        shape = CircleShape,
                    )
                    .pointerInput(canRecord) {
                        detectTapGestures(
                            onPress = {
                                if (!enabled) return@detectTapGestures
                                if (!micPermission.status.isGranted) {
                                    micPermission.launchPermissionRequest()
                                    return@detectTapGestures
                                }
                                if (speechRecognizer != null) {
                                    isListening = true
                                    onListeningChanged(true)
                                    partialResult = ""
                                    speechRecognizer.startListening(recognizerIntent)
                                    tryAwaitRelease()
                                    if (isListening) {
                                        speechRecognizer.stopListening()
                                    }
                                }
                            },
                        )
                    },
            ) {
                Icon(
                    imageVector = if (canRecord) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (canRecord) "Voice input" else "Microphone permission required",
                    tint = if (canRecord) Color.White else MutedTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── Color-lerp utility (Compose doesn't expose this publicly) ──────────────────
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}

/**
 * Text-to-Speech helper for voice conversation mode.
 */
class TtsHelper(context: android.content.Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    var isSpeaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isReady) {
            isSpeaking = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onDone?.invoke()
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onDone?.invoke()
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openclaw-tts")
        }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
