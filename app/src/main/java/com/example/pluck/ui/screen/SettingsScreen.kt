package com.example.pluck.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.HapticMode
import com.example.pluck.domain.model.ThemeMode
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LocalFloatingNavigationBarClearance
import com.example.pluck.ui.components.ObserveFloatingNavigationScroll
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, onLocalAi: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val floatingBarClearance = LocalFloatingNavigationBarClearance.current
    val cloudProviders = remember { AiProvider.entries.filter { it.requiresApiKey } }
    var keyEditorProvider by remember { mutableStateOf(AiProvider.GEMINI) }

    LaunchedEffect(state.provider) {
        if (state.provider.requiresApiKey) keyEditorProvider = state.provider
    }

    ObserveFloatingNavigationScroll(scrollState)
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Settings", "Your keys stay on this device", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            AppearanceCard(
                selected = state.themeMode,
                dynamicColor = state.dynamicColor,
                onThemeSelect = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor
            )
            Text("Story engine", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            ProviderSelector(state.provider) { provider ->
                viewModel.select(provider)
                if (provider == AiProvider.LOCAL_GEMMA) onLocalAi()
            }
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Lock, null); Text("Private by design", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp)) }
                    Text("API keys are encrypted with Android Keystore-backed storage. Pluck has no account and no cloud database.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Feedback", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            HapticModeCard(selected = state.hapticMode, onSelect = viewModel::setHapticMode)
            Text("API keys", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Keep multiple providers ready without expanding the page. Choose one key to edit or test.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ApiKeyVault(
                provider = keyEditorProvider,
                providers = cloudProviders,
                keys = state.keys,
                isPreferredProvider = state.provider == keyEditorProvider,
                value = state.keys[keyEditorProvider].orEmpty(),
                testing = state.testing == keyEditorProvider,
                result = state.result?.takeIf { it.first == keyEditorProvider }?.second,
                onProviderSelect = { keyEditorProvider = it },
                onValueChange = { viewModel.updateKey(keyEditorProvider, it) },
                onSave = { viewModel.saveKey(keyEditorProvider) },
                onTest = { viewModel.test(keyEditorProvider) }
            )
            LocalAiSettingsCard(
                onClick = onLocalAi,
                modifier = Modifier.fillMaxWidth().padding(bottom = floatingBarClearance + 24.dp)
            )
        }
    }
}

@Composable
private fun LocalAiSettingsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Local AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Download the verified LiteRT Community model once, then create stories without uploading photos or prompts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill("Manage on-device model")
            }
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = "Open Local AI",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppearanceCard(
    selected: ThemeMode,
    dynamicColor: Boolean,
    onThemeSelect: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App theme", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose how Pluck looks across the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onThemeSelect(mode) },
                        label = { Text(mode.displayName) },
                        leadingIcon = if (selected == mode) {
                            { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.padding(start = 2.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use device colors", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (selected == ThemeMode.AMOLED_BLACK) {
                            "Keep true black surfaces while using device colors only as subtle accents."
                        } else {
                            "Match Pluck to your phone’s dynamic Material You color palette."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
            }
        }
    }
}

@Composable
private fun HapticModeCard(selected: HapticMode, onSelect: (HapticMode) -> Unit) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Haptic feedback", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose how much touch feedback Pluck gives for your direct actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HapticMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(mode.displayName) },
                        leadingIcon = if (selected == mode) {
                            { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.padding(start = 2.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
            Text(
                text = when (selected) {
                    HapticMode.OFF -> "No in-app touch feedback."
                    HapticMode.ESSENTIAL -> "Primary actions, camera capture, errors, and destructive actions only."
                    HapticMode.FULL -> "Includes navigation and back actions as well."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderSelector(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExpressiveCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(end = 14.dp)) { Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(10.dp), MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f)) {
                Text("Preferred provider", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.displayName, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Rounded.ExpandMore, "Choose provider")
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AiProvider.entries.forEach { provider -> DropdownMenuItem(text = { Text(provider.displayName) }, onClick = { onSelect(provider); expanded = false }, trailingIcon = { if (provider == selected) Icon(Icons.Rounded.CheckCircle, null) }) }
            }
        }
    }
}

@Composable
private fun ApiKeyVault(
    provider: AiProvider,
    providers: List<AiProvider>,
    keys: Map<AiProvider, String>,
    isPreferredProvider: Boolean,
    value: String,
    testing: Boolean,
    result: ConnectionResult?,
    onProviderSelect: (AiProvider) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    var reveal by remember(provider) { mutableStateOf(false) }
    val savedKeyCount = providers.count { keys[it].isNullOrBlank().not() }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Provider key vault", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$savedKeyCount of ${providers.size} keys saved securely",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPreferredProvider) StatusPill("Default")
            }

            Text("Editing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                androidx.compose.material3.Surface(
                    onClick = { pickerExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (value.isBlank()) "No key saved" else "Key saved on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose provider")
                    }
                }
                DropdownMenu(
                    expanded = pickerExpanded,
                    onDismissRequest = { pickerExpanded = false }
                ) {
                    providers.forEach { candidate ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(candidate.displayName)
                                    Text(
                                        if (keys[candidate].isNullOrBlank()) "No key saved" else "Key saved on this device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProviderSelect(candidate)
                                pickerExpanded = false
                            },
                            trailingIcon = {
                                if (candidate == provider) Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${provider.displayName} API key") },
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            imageVector = if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (reveal) "Hide key" else "Reveal key"
                        )
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSave, enabled = value.isNotBlank()) { Text("Save key") }
                AnimatedPrimaryButton(
                    text = if (testing) "Testing…" else "Test connection",
                    onClick = onTest,
                    enabled = !testing
                )
            }
            AnimatedVisibility(result != null, enter = fadeIn() + expandVertically(), exit = fadeOut()) {
                result?.let { ConnectionStatus(it) }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(result: ConnectionResult) {
    val (message, active) = when (result) {
        ConnectionResult.Connected -> "Connected" to true
        ConnectionResult.InvalidKey -> "Invalid API key" to false
        ConnectionResult.NetworkError -> "Network error" to false
        is ConnectionResult.Failed -> result.message to false
    }
    StatusPill(message, active)
}
