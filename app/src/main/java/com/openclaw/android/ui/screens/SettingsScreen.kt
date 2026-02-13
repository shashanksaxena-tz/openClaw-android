package com.openclaw.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.llm.ModelRouter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    modelRouter: ModelRouter,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var geminiKey by remember { mutableStateOf(settings.getGeminiKey()) }
    var groqKey by remember { mutableStateOf(settings.getGroqKey()) }
    var cerebrasKey by remember { mutableStateOf(settings.getCerebrasKey()) }
    var selectedModel by remember { mutableStateOf(settings.getDefaultModel()) }
    var systemPrompt by remember { mutableStateOf(settings.getSystemPrompt()) }
    var showModelPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // API Keys section
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Keys are stored encrypted on device. Never sent anywhere except to the respective API provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            ApiKeyField(
                label = "Gemini API Key",
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    scope.launch { settings.setGeminiKey(it) }
                },
                hint = "Free tier: 1500 req/day",
            )

            Spacer(Modifier.height(12.dp))

            ApiKeyField(
                label = "Groq API Key",
                value = groqKey,
                onValueChange = {
                    groqKey = it
                    scope.launch { settings.setGroqKey(it) }
                },
                hint = "Free tier: fast inference",
            )

            Spacer(Modifier.height(12.dp))

            ApiKeyField(
                label = "Cerebras API Key",
                value = cerebrasKey,
                onValueChange = {
                    cerebrasKey = it
                    scope.launch { settings.setCerebrasKey(it) }
                },
                hint = "Free tier: fastest inference",
            )

            Spacer(Modifier.height(24.dp))

            // Model selection
            Text(
                text = "Default Model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The model used for text conversations. Vision-capable models are auto-selected when sharing images.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val availableModels = modelRouter.getAvailableModels()
            val currentModel = availableModels.find { (_, info) -> info.id == selectedModel }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showModelPicker = true },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentModel?.second?.displayName ?: "Auto (best available)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (currentModel != null) {
                        Text(
                            text = "${currentModel.first.displayName} - ${currentModel.second.contextWindow / 1000}K context",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showModelPicker) {
                AlertDialog(
                    onDismissRequest = { showModelPicker = false },
                    title = { Text("Select Model") },
                    text = {
                        Column {
                            // Auto option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedModel = ""
                                        settings.setDefaultModel("")
                                        showModelPicker = false
                                    }
                                    .padding(12.dp),
                            ) {
                                RadioButton(
                                    selected = selectedModel.isBlank(),
                                    onClick = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Auto (best available)")
                            }

                            for ((provider, model) in availableModels) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedModel = model.id
                                            settings.setDefaultModel(model.id)
                                            showModelPicker = false
                                        }
                                        .padding(12.dp),
                                ) {
                                    RadioButton(
                                        selected = selectedModel == model.id,
                                        onClick = null,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            text = provider.displayName +
                                                    (if (model.supportsVision) " | Vision" else "") +
                                                    " | ${model.contextWindow / 1000}K",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showModelPicker = false }) {
                            Text("Close")
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // System prompt
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = {
                    systemPrompt = it
                    settings.setSystemPrompt(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(32.dp))

            // Info
            Text(
                text = "OpenClaw Android v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(hint) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
    )
}
