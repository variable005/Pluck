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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Local AI", "Private stories, even offline", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LocalHero()
            ModelCard(state, onDownload = { showConsent = true }, onRefresh = viewModel::refresh)
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Security, null); Text("Verified by Android", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp)) }
                    Text("The model is downloaded and integrity-checked by Google’s AICore service. Pluck never accepts model files, URLs, or manual imports.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("AICore manages storage, updates, integrity verification, and hardware acceleration. Model files are not visible to Pluck or other apps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
    if (showConsent) DownloadConsent(onConfirm = { showConsent = false; viewModel.download() }, onDismiss = { showConsent = false })
}

@Composable
private fun LocalHero() {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Text("Your journey stays here.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
            Text("After one consented system download, photos and prompts stay on your device while Pluck writes your story.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ModelCard(state: LocalAiModelState, onDownload: () -> Unit, onRefresh: () -> Unit) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.modelName, style = MaterialTheme.typography.titleLarge)
            Text("Google AICore · system-managed on-device model", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(statusLabel(state), active = state.status == LocalAiInstallStatus.INSTALLED)
            if (state.status == LocalAiInstallStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { state.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)}", style = MaterialTheme.typography.labelLarge)
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (state.status) {
                LocalAiInstallStatus.NOT_INSTALLED -> AnimatedPrimaryButton("Download securely", onDownload, Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.Download, null) })
                LocalAiInstallStatus.FAILED -> AnimatedPrimaryButton("Try again", onRefresh, Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.Refresh, null) })
                LocalAiInstallStatus.UNAVAILABLE -> androidx.compose.material3.OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text("Check again", Modifier.padding(start = 8.dp)) }
                else -> androidx.compose.material3.OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text("Refresh status", Modifier.padding(start = 8.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadConsent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Local AI?") },
        text = { Text("Google’s AICore service will download and verify the on-device model. The size and exact storage location are supplied by Android. After installation, Pluck can generate stories without sending photos or prompts to a server.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

private fun statusLabel(state: LocalAiModelState): String = when (state.status) {
    LocalAiInstallStatus.CHECKING -> "Checking availability"
    LocalAiInstallStatus.NOT_INSTALLED -> "Ready to download"
    LocalAiInstallStatus.DOWNLOADING -> "Downloading"
    LocalAiInstallStatus.INSTALLED -> "Installed · offline ready"
    LocalAiInstallStatus.UNAVAILABLE -> "Unavailable on this device"
    LocalAiInstallStatus.FAILED -> "Needs attention"
}

private fun formatBytes(value: Long): String = when {
    value <= 0 -> "Calculating size"
    value < 1_000_000 -> "${value / 1_000} KB"
    else -> "%.1f GB".format(value / 1_000_000_000.0)
}
