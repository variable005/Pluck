package com.example.pluck.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
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
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, onLocalAi: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Settings", "Your keys stay on this device", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Text("Only the active provider needs a key. You can keep keys for multiple providers ready to switch.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiProvider.entries.filter { it.requiresApiKey }.forEach { provider ->
                ApiKeyCard(provider, state.keys[provider].orEmpty(), selected = state.provider == provider, testing = state.testing == provider, result = state.result?.takeIf { it.first == provider }?.second, onValueChange = { viewModel.updateKey(provider, it) }, onSave = { viewModel.saveKey(provider) }, onTest = { viewModel.test(provider) })
            }
            ExpressiveCard(onClick = onLocalAi, modifier = Modifier.fillMaxWidth().padding(bottom = 120.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Local AI", style = MaterialTheme.typography.titleMedium)
                    Text("Download Google’s verified on-device model once, then create stories without uploading photos or prompts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    StatusPill("Manage on-device model")
                }
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("Use wallpaper colors", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Use Android’s dynamic Material You color palette when available.",
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
private fun ApiKeyCard(provider: AiProvider, value: String, selected: Boolean, testing: Boolean, result: ConnectionResult?, onValueChange: (String) -> Unit, onSave: () -> Unit, onTest: () -> Unit) {
    var reveal by remember(provider) { mutableStateOf(false) }
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(if (selected) "Currently selected" else "Ready to connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) StatusPill("Active")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide key" else "Reveal key") } }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSave, enabled = value.isNotBlank()) { Text("Save key") }
                AnimatedPrimaryButton(if (testing) "Testing…" else "Test connection", onTest, enabled = !testing)
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
