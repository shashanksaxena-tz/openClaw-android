package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settings: SettingsRepository,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        // Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (pagerState.currentPage < 2) {
                TextButton(onClick = onComplete) {
                    Text("Skip")
                }
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ApiKeyPage(settings)
                2 -> TryItPage(onComplete)
            }
        }

        // Page indicator + navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(64.dp))
            }

            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                    )
                }
            }

            // Next / Get Started
            when (pagerState.currentPage) {
                2 -> {
                    Button(onClick = onComplete) {
                        Text("Get Started")
                    }
                }
                1 -> {
                    Button(
                        onClick = {
                            if (settings.hasAnyApiKey()) {
                                scope.launch { pagerState.animateScrollToPage(2) }
                            }
                        },
                        enabled = settings.hasAnyApiKey(),
                    ) {
                        Text("Next")
                    }
                }
                else -> {
                    Button(onClick = { scope.launch { pagerState.animateScrollToPage(1) } }) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "welcome")
        val iconScale by infiniteTransition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse),
            label = "icon",
        )

        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(72.dp).scale(iconScale),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Welcome to OpenClaw",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your personal AI assistant",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Manage files, organize projects, browse the web,\nand have voice conversations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(32.dp))

        val features = listOf(
            "Create & manage files" to Icons.Default.Folder,
            "Organize into spaces" to Icons.Default.Workspaces,
            "Share from any app" to Icons.Default.Share,
            "Voice conversations" to Icons.Default.Mic,
            "Web search built-in" to Icons.Default.Search,
            "Multiple AI providers" to Icons.Default.Memory,
        )
        for ((text, icon) in features) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyPage(settings: SettingsRepository) {
    val context = LocalContext.current
    var geminiKey by remember { mutableStateOf(settings.getGeminiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf("gemini") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Connect your AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your API key stays encrypted on your device.\nWe recommend Google Gemini — it's free!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Provider cards
        val providers = listOf(
            Triple("gemini", "Google Gemini", "Free: 250-1500 req/day, vision, 1M context"),
            Triple("groq", "Groq", "Free: fast inference, open-source models"),
            Triple("cerebras", "Cerebras", "Free: fastest inference speed"),
        )

        for ((id, name, desc) in providers) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = if (selectedProvider == id) 2.dp else 1.dp,
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selectedProvider == id)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface,
                ),
                onClick = { selectedProvider = id },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedProvider == id, onClick = { selectedProvider = id })
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // API key input
        OutlinedTextField(
            value = when (selectedProvider) {
                "gemini" -> geminiKey
                "groq" -> remember { mutableStateOf(settings.getGroqKey()) }.value
                "cerebras" -> remember { mutableStateOf(settings.getCerebrasKey()) }.value
                else -> ""
            },
            onValueChange = { key ->
                when (selectedProvider) {
                    "gemini" -> { geminiKey = key; settings.setGeminiKey(key) }
                    "groq" -> settings.setGroqKey(key)
                    "cerebras" -> settings.setCerebrasKey(key)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Toggle visibility")
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        // Get key button
        val url = when (selectedProvider) {
            "gemini" -> "https://aistudio.google.com/apikey"
            "groq" -> "https://console.groq.com/keys"
            "cerebras" -> "https://cloud.cerebras.ai/"
            else -> null
        }
        url?.let {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Free API Key")
            }
        }
    }
}

@Composable
private fun TryItPage(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Try these to get started:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        val tips = listOf(
            "\"Help me write a grocery list\"",
            "\"Create a project plan in markdown\"",
            "Share a photo and ask \"What's in this image?\"",
            "Create a Space to organize a project",
        )
        for (tip in tips) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp, horizontal = 16.dp),
            ) {
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
