package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.data.Space
import com.openclaw.android.data.SpaceManager
import com.openclaw.android.llm.ModelRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    modelRouter: ModelRouter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spaceManager: SpaceManager? = null,
    agentRuntime: AgentRuntime? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var geminiKey by remember { mutableStateOf(settings.getGeminiKey()) }
    var groqKey by remember { mutableStateOf(settings.getGroqKey()) }
    var cerebrasKey by remember { mutableStateOf(settings.getCerebrasKey()) }
    var selectedModel by remember { mutableStateOf(settings.getDefaultModel()) }
    var systemPrompt by remember { mutableStateOf(settings.getSystemPrompt()) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showCreateSpace by remember { mutableStateOf(false) }
    var spaces by remember { mutableStateOf(spaceManager?.getSpaces() ?: emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface)

        Spacer(Modifier.height(24.dp))

        // === API Keys Section ===
        Text("API Keys", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text("Keys are encrypted on device. Never sent anywhere except to the API provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        DebouncedApiKeyField(
            label = "Gemini API Key",
            value = geminiKey,
            onValueChange = { geminiKey = it },
            onSave = { settings.setGeminiKey(it) },
            hint = "Free: 250-1500 req/day, vision, 1M context",
            getKeyUrl = "https://aistudio.google.com/apikey",
            validateFormat = { it.length >= 30 },
        )
        Spacer(Modifier.height(12.dp))

        DebouncedApiKeyField(
            label = "Groq API Key",
            value = groqKey,
            onValueChange = { groqKey = it },
            onSave = { settings.setGroqKey(it) },
            hint = "Free: fast inference, open-source models",
            getKeyUrl = "https://console.groq.com/keys",
            validateFormat = { it.startsWith("gsk_") },
        )
        Spacer(Modifier.height(12.dp))

        DebouncedApiKeyField(
            label = "Cerebras API Key",
            value = cerebrasKey,
            onValueChange = { cerebrasKey = it },
            onSave = { settings.setCerebrasKey(it) },
            hint = "Free: fastest inference speed",
            getKeyUrl = "https://cloud.cerebras.ai/",
            validateFormat = { it.length >= 20 },
        )

        Spacer(Modifier.height(24.dp))

        // === Model Selection ===
        Text("Default Model", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))

        val availableModels = modelRouter.getAvailableModels()
        val currentModel = availableModels.find { (_, info) -> info.id == selectedModel }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { showModelPicker = true },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(currentModel?.second?.displayName ?: "Auto (best available)",
                    style = MaterialTheme.typography.bodyLarge)
                if (currentModel != null) {
                    Text("${currentModel.first.displayName} · ${currentModel.second.contextWindow / 1000}K context" +
                            if (currentModel.second.supportsVision) " · Vision" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (showModelPicker) {
            AlertDialog(
                onDismissRequest = { showModelPicker = false },
                title = { Text("Select Model") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { selectedModel = ""; settings.setDefaultModel(""); showModelPicker = false }
                                .padding(12.dp),
                        ) {
                            RadioButton(selected = selectedModel.isBlank(), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto (best available)")
                        }
                        for ((provider, model) in availableModels) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { selectedModel = model.id; settings.setDefaultModel(model.id); showModelPicker = false }
                                    .padding(12.dp),
                            ) {
                                RadioButton(selected = selectedModel == model.id, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(model.displayName)
                                    Text("${provider.displayName}" +
                                            (if (model.supportsVision) " · Vision" else "") +
                                            " · ${model.contextWindow / 1000}K",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Close") } },
            )
        }

        // === Spaces Section ===
        if (spaceManager != null && agentRuntime != null) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spaces", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreateSpace = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Organize projects with separate files and AI context.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            val activeSpaceName = agentRuntime.activeSpaceName

            for (space in spaces) {
                val isActive = activeSpaceName?.contains(space.name) == true
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface),
                    onClick = {
                        if (isActive) agentRuntime.setActiveSpace(null)
                        else {
                            agentRuntime.setActiveSpace(space.id)
                            scope.launch { agentRuntime.startNewConversation() }
                        }
                    },
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(space.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(space.name, style = MaterialTheme.typography.bodyMedium)
                            if (space.description.isNotBlank()) {
                                Text(space.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        if (isActive) {
                            Icon(Icons.Default.CheckCircle, "Active", Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (space.id != "default") {
                            IconButton(onClick = {
                                spaceManager.deleteSpace(space.id)
                                if (isActive) agentRuntime.setActiveSpace(null)
                                spaces = spaceManager.getSpaces()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            if (showCreateSpace) {
                CreateSpaceDialog(
                    onDismiss = { showCreateSpace = false },
                    onCreate = { name, emoji, desc ->
                        spaceManager.createSpace(name, emoji, desc)
                        spaces = spaceManager.getSpaces()
                        showCreateSpace = false
                    },
                )
            }
        }

        // === System Prompt ===
        Spacer(Modifier.height(24.dp))
        Text("System Prompt", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it; settings.setSystemPrompt(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 10,
            textStyle = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(32.dp))
        Text("OpenClaw Android v0.4.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun DebouncedApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: (String) -> Unit,
    hint: String,
    getKeyUrl: String,
    validateFormat: (String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    val isValid = value.isBlank() || validateFormat(value)

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                saveJob?.cancel()
                saveJob = scope.launch {
                    delay(500)
                    onSave(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            supportingText = { Text(hint) },
            singleLine = true,
            isError = !isValid,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    if (value.isNotBlank()) {
                        Icon(
                            if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                            null, Modifier.size(20.dp),
                            tint = if (isValid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            if (visible) "Hide" else "Show")
                    }
                }
            },
        )
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getKeyUrl))) },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Get Free API Key", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("\uD83D\uDCC1") }
    var description by remember { mutableStateOf("") }
    val emojiOptions = listOf("\uD83D\uDCC1", "\uD83D\uDCBC", "\uD83D\uDCDD", "\uD83C\uDFA8", "\uD83D\uDD2C", "\uD83D\uDCCA", "\uD83C\uDFB5", "\uD83D\uDCF8", "\uD83C\uDFE0", "\u2708\uFE0F", "\uD83C\uDF73", "\uD83D\uDCAA")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Space") },
        text = {
            Column {
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (e in emojiOptions.take(6)) {
                        FilterChip(selected = emoji == e, onClick = { emoji = e },
                            label = { Text(e) }, modifier = Modifier.height(32.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (e in emojiOptions.drop(6)) {
                        FilterChip(selected = emoji == e, onClick = { emoji = e },
                            label = { Text(e) }, modifier = Modifier.height(32.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Space Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), emoji, description.trim()) },
                enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
