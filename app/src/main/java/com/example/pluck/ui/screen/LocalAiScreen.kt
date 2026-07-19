package com.example.pluck.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.domain.model.LocalAiInstallStatus
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.LocalAiViewModel

@Composable
fun LocalAiScreen(onBack: () -> Unit, viewModel: LocalAiViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showConsent by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Local AI", "Private Gemma stories on this phone", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalHero()
            ModelCard(
                state = state,
                onDownload = { showConsent = true },
                onPause = viewModel::pause,
                onVerify = viewModel::verify,
                onDelete = { showDelete = true },
                onRefresh = viewModel::refresh,
                onCheckUpdates = viewModel::checkForUpdates
            )
            SecurityCard()
            Text(
                "LiteRT-LM runs the verified model entirely on-device. After installation, Pluck never needs the internet to analyze journey photos or write stories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
    }
    if (showConsent) DownloadConsent(onConfirm = { showConsent = false; viewModel.download() }, onDismiss = { showConsent = false })
    if (showDelete) DeleteConfirmation(onConfirm = { showDelete = false; viewModel.delete() }, onDismiss = { showDelete = false })
}

@Composable
private fun LocalHero() {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Text("Your journey stays here.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Download Gemma once, then create stories in airplane mode. Photos, prompts, and generated text stay on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ModelCard(
    state: LocalAiModelState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.modelName, style = MaterialTheme.typography.titleLarge)
            Text("${state.publisher} · ${state.modelVersion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(statusLabel(state), active = state.status == LocalAiInstallStatus.INSTALLED)
            if (state.status == LocalAiInstallStatus.DOWNLOADING || state.status == LocalAiInstallStatus.VERIFYING) {
                LinearProgressIndicator(progress = { state.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)}", style = MaterialTheme.typography.labelLarge)
            }
            StorageDetails(state)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (state.status) {
                LocalAiInstallStatus.NOT_INSTALLED, LocalAiInstallStatus.FAILED, LocalAiInstallStatus.CORRUPTED -> {
                    AnimatedPrimaryButton(if (state.status == LocalAiInstallStatus.NOT_INSTALLED) "Download securely" else "Retry secure download", onDownload, Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.Download, null) })
                }
                LocalAiInstallStatus.PAUSED -> {
                    AnimatedPrimaryButton("Resume download", onDownload, Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.Download, null) })
                    SecondaryAction("Delete partial download", Icons.Rounded.DeleteOutline, onDelete)
                }
                LocalAiInstallStatus.DOWNLOADING -> SecondaryAction("Pause download", Icons.Rounded.Pause, onPause)
                LocalAiInstallStatus.VERIFYING -> Text("Please keep Pluck open while verification completes.", style = MaterialTheme.typography.labelLarge)
                LocalAiInstallStatus.INSTALLED -> {
                    if (state.updateAvailable) Text("A newer official release is available.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    SecondaryAction("Verify model", Icons.Rounded.VerifiedUser, onVerify)
                    SecondaryAction("Check for updates", Icons.Rounded.Refresh, onCheckUpdates)
                    SecondaryAction("Delete model", Icons.Rounded.DeleteOutline, onDelete)
                }
                LocalAiInstallStatus.CHECKING -> SecondaryAction("Refresh status", Icons.Rounded.Refresh, onRefresh)
            }
        }
    }
}

@Composable
private fun StorageDetails(state: LocalAiModelState) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Model download", style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(state.totalBytes), style = MaterialTheme.typography.labelLarge)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Required free storage", style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(state.requiredStorageBytes), style = MaterialTheme.typography.labelLarge)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Available now", style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(state.availableStorageBytes), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SecurityCard() {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null)
                Text("Fixed source. Verified model.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            Text(
                "Pluck accepts one immutable Google AI Edge LiteRT Community release over HTTPS, verifies its SHA-256 before installation, and keeps it in app-private no-backup storage. There are no manual URLs, imports, or sideloaded models.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadConsent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Local Gemma?") },
        text = { Text("Gemma 4 E2B is a one-time 2.58 GB download. Pluck reserves about 4.5 GB of private storage for the download and runtime cache. It downloads only the fixed Google AI Edge LiteRT Community release over HTTPS, verifies its SHA-256, and never accepts a user-supplied model. After verification, stories work offline.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@Composable
private fun DeleteConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Local Gemma?") },
        text = { Text("This removes the private model, download data, and LiteRT cache from this device. Your journeys and saved stories stay untouched.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep model") } }
    )
}

private fun statusLabel(state: LocalAiModelState): String = when (state.status) {
    LocalAiInstallStatus.CHECKING -> "Checking local model"
    LocalAiInstallStatus.NOT_INSTALLED -> "Ready to download"
    LocalAiInstallStatus.DOWNLOADING -> "Downloading securely"
    LocalAiInstallStatus.PAUSED -> "Download paused"
    LocalAiInstallStatus.VERIFYING -> "Verifying integrity"
    LocalAiInstallStatus.INSTALLED -> "Installed · offline ready"
    LocalAiInstallStatus.CORRUPTED -> "Invalid download removed"
    LocalAiInstallStatus.FAILED -> "Needs attention"
}

private fun formatBytes(value: Long): String = when {
    value <= 0 -> "Calculating"
    value < 1_000_000 -> "${value / 1_000} KB"
    else -> "%.1f GB".format(value / 1_000_000_000.0)
}
