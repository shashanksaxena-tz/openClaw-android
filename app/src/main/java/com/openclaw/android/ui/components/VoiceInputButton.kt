package com.openclaw.android.ui.components

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale

/**
 * Voice input button — shows equalizer-style icon like the reference design.
 * Hold to record, release to send the recognised text.
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

    // SpeechRecognizer must be created on the main/Looper thread.
    // Using mutableStateOf + LaunchedEffect(Dispatchers.Main) ensures this.
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
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
                if (text.isNotBlank()) onResult(text)
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
        onDispose { speechRecognizer?.destroy() }
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "buttonScale",
    )

    val canRecord = micPermission.status.isGranted && speechRecognizer != null
    val bgColor = MaterialTheme.colorScheme.onSurface
    val iconColor = MaterialTheme.colorScheme.surface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .background(
                when {
                    isListening -> MaterialTheme.colorScheme.error
                    !canRecord -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> bgColor
                },
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
            imageVector = when {
                isListening -> Icons.Default.Mic
                canRecord -> Icons.Default.GraphicEq  // Equalizer icon like reference
                else -> Icons.Default.MicOff
            },
            contentDescription = when {
                isListening -> "Recording..."
                canRecord -> "Hold to record"
                else -> "Microphone permission required"
            },
            tint = when {
                isListening -> Color.White
                canRecord -> iconColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
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
