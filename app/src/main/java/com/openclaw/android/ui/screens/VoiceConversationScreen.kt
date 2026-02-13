package com.openclaw.android.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.openclaw.android.agent.AgentEvent
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.AgentState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

/**
 * Full-screen voice conversation mode. Like a phone call with the AI.
 * Flow: Listen → Send to LLM → TTS response → Auto-listen again
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
                    voiceState = VoiceState.IDLE
                    // Auto-listen after speaking
                    if (autoListen && micPermission.status.isGranted && speechRecognizer != null) {
                        scope.launch {
                            delay(500)
                            startListening(speechRecognizer, recognizerIntent)
                            voiceState = VoiceState.LISTENING
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    voiceState = VoiceState.IDLE
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

    // UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    tts?.stop()
                    speechRecognizer?.cancel()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, "Close")
                }
                Text(
                    "Voice Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Auto-listen toggle
                IconButton(onClick = { autoListen = !autoListen }) {
                    Icon(
                        if (autoListen) Icons.Default.Loop else Icons.Default.StopCircle,
                        if (autoListen) "Auto-listen on" else "Auto-listen off",
                        tint = if (autoListen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Status display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // Animated orb
                val infiniteTransition = rememberInfiniteTransition(label = "orb")
                val orbScale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = when (voiceState) {
                                VoiceState.LISTENING -> 600
                                VoiceState.PROCESSING -> 400
                                VoiceState.SPEAKING -> 800
                                VoiceState.IDLE -> 2000
                            },
                            easing = EaseInOutSine,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "scale",
                )

                val orbColor = when (voiceState) {
                    VoiceState.LISTENING -> MaterialTheme.colorScheme.error
                    VoiceState.PROCESSING -> MaterialTheme.colorScheme.secondary
                    VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary
                    VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(orbColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(orbColor.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when (voiceState) {
                                VoiceState.LISTENING -> Icons.Default.Mic
                                VoiceState.PROCESSING -> Icons.Default.Psychology
                                VoiceState.SPEAKING -> Icons.Default.VolumeUp
                                VoiceState.IDLE -> Icons.Default.Mic
                            },
                            null,
                            Modifier.size(36.dp),
                            tint = orbColor,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Status text
                Text(
                    text = when (voiceState) {
                        VoiceState.LISTENING -> "Listening..."
                        VoiceState.PROCESSING -> "Thinking..."
                        VoiceState.SPEAKING -> "Speaking..."
                        VoiceState.IDLE -> "Tap the mic to start"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(12.dp))

                // Transcribed / response text
                val displayText = when (voiceState) {
                    VoiceState.LISTENING -> transcribedText.ifBlank { "..." }
                    VoiceState.PROCESSING -> transcribedText
                    VoiceState.SPEAKING -> responseText.take(200) + if (responseText.length > 200) "..." else ""
                    VoiceState.IDLE -> ""
                }
                if (displayText.isNotBlank()) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        maxLines = 4,
                    )
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Stop speaking button
                if (voiceState == VoiceState.SPEAKING) {
                    FilledTonalButton(
                        onClick = {
                            tts?.stop()
                            voiceState = VoiceState.IDLE
                        },
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                // Main mic button
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
                    modifier = Modifier.size(72.dp),
                    containerColor = when (voiceState) {
                        VoiceState.LISTENING -> MaterialTheme.colorScheme.error
                        VoiceState.PROCESSING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                ) {
                    Icon(
                        when (voiceState) {
                            VoiceState.LISTENING -> Icons.Default.Stop
                            VoiceState.PROCESSING -> Icons.Default.Close
                            else -> Icons.Default.Mic
                        },
                        "Voice",
                        Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                // End call button
                if (voiceState != VoiceState.IDLE) {
                    FilledTonalButton(
                        onClick = {
                            tts?.stop()
                            speechRecognizer?.cancel()
                            runtime.cancel()
                            voiceState = VoiceState.IDLE
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.CallEnd, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("End")
                    }
                }
            }
        }
    }
}

private fun startListening(recognizer: SpeechRecognizer, intent: Intent) {
    try {
        recognizer.startListening(intent)
    } catch (_: Exception) {
        // Ignore if recognizer is in bad state
    }
}
