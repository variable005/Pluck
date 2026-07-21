package com.example.pluck.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.domain.model.LocalAiInstallStatus
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.LocalAiViewModel
import com.example.pluck.widget.CaptureNextPlaceWidgetProvider

@Composable
fun LocalAiScreen(onBack: () -> Unit, viewModel: LocalAiViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showConsent by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.status, state.verified) {
        CaptureNextPlaceWidgetProvider.refreshInstalledWidgets(context)
    }
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Local AI", "Private Gemma stories on this phone", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalHero()
            LocalReadinessCard(state)
            ModelCard(
                state = state,
                onDownload = { showConsent = true },
                onPause = viewModel::pause,
                onVerify = viewModel::verify,
                onDelete = { showDelete = true },
                onRefresh = viewModel::refresh,
                onCheckUpdates = viewModel::checkForUpdates
            )
            GalaxyS23EstimateCard(state)
            BatteryAndThermalGuidanceCard(state)
            SecurityCard()
            Text(
                "LiteRT-LM runs the verified model entirely on-device. After installation, Pluck never needs the internet to analyze journey photos or write stories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 120.dp)
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
private fun LocalReadinessCard(state: LocalAiModelState) {
    val offlineReady = state.isOfflineReady()
    val icon = when (state.status) {
        LocalAiInstallStatus.INSTALLED -> Icons.Rounded.VerifiedUser
        LocalAiInstallStatus.DOWNLOADING, LocalAiInstallStatus.PAUSED, LocalAiInstallStatus.VERIFYING -> Icons.Rounded.Download
        else -> Icons.Rounded.CloudOff
    }
    val title = when {
        offlineReady -> "Ready for offline stories"
        state.status == LocalAiInstallStatus.DOWNLOADING -> "Preparing Local Gemma"
        state.status == LocalAiInstallStatus.VERIFYING -> "Checking your model"
        state.status == LocalAiInstallStatus.PAUSED -> "Download is paused"
        else -> "Local stories need a model"
    }
    val body = when {
        offlineReady -> "Local Gemma is verified. You can disconnect from the internet and keep creating stories privately."
        state.status == LocalAiInstallStatus.DOWNLOADING -> "The model is downloading securely. Local stories will be ready as soon as integrity verification completes."
        state.status == LocalAiInstallStatus.VERIFYING -> "Pluck is checking the download before it can ever be used."
        state.status == LocalAiInstallStatus.PAUSED -> "Resume the secure download whenever you have a reliable connection and enough free storage."
        else -> "Download and verify Gemma once to make story generation available without a connection."
    }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (offlineReady) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (offlineReady) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Local Gemma readiness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            }
            StatusPill(
                text = if (offlineReady) "Offline ready" else statusLabel(state),
                active = offlineReady
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            ReadinessDetail(
                label = "Model integrity",
                value = if (state.verified) "Verified" else "Not verified"
            )
            ReadinessDetail(
                label = "Connection after setup",
                value = if (offlineReady) "Not required" else "Required once"
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("On-device storage", style = MaterialTheme.typography.titleMedium)
        ReadinessDetail(
            label = "Installed model",
            value = state.downloadedBytes.takeIf { it > 0 }?.let(::formatBytes) ?: "Not installed"
        )
        ReadinessDetail(label = "Download size", value = formatBytes(state.totalBytes))
        ReadinessDetail(label = "Space needed to install", value = formatBytes(state.requiredStorageBytes))
        ReadinessDetail(label = "Free on this device", value = formatBytes(state.availableStorageBytes))
        Text(
            "The model and its runtime cache remain in Pluck's private app storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GalaxyS23EstimateCard(state: LocalAiModelState) {
    val offlineReady = state.isOfflineReady()
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Galaxy S23 estimate", style = MaterialTheme.typography.titleMedium)
                    Text("A useful expectation, not a countdown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EstimateMetric(
                    label = "Typical journey",
                    value = "4 places",
                    modifier = Modifier.weight(1f)
                )
                EstimateMetric(
                    label = "Story time",
                    value = "3–6 min",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                if (offlineReady) {
                    "For a 700–1,200 word story, expect roughly 3–6 minutes after you tap Generate. More photos, a warm phone, and longer output can take longer."
                } else {
                    "After the model is verified, a typical 4-place, 700–1,200 word story usually takes about 3–6 minutes on a Galaxy S23."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EstimateMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BatteryAndThermalGuidanceCard(state: LocalAiModelState) {
    val offlineReady = state.isOfflineReady()
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Battery & warmth", style = MaterialTheme.typography.titleMedium)
                    Text("A few habits keep local writing comfortable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GuidanceDetail(
                title = "Start with charge",
                body = "Use 30% battery or more. For longer stories, connecting power is the most reliable option."
            )
            GuidanceDetail(
                title = "Give a warm phone a break",
                body = "If your Galaxy S23 feels hot, let it cool before generating. Android can temporarily reduce speed when the device is warm."
            )
            GuidanceDetail(
                title = "Keep this screen simple",
                body = "For the most predictable result, avoid recording video or playing demanding games while Pluck writes."
            )
            if (!offlineReady) {
                Text(
                    "These suggestions apply once Local Gemma has finished verification.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GuidanceDetail(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadinessDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
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
                "Pluck uses one fixed Google-documented LiteRT Community model distribution, delivered through Hugging Face over HTTPS. It verifies the pinned SHA-256 before installation and keeps the model in app-private no-backup storage. There are no manual URLs, imports, or sideloaded models.",
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
        text = { Text("Gemma 4 E2B is a one-time 2.58 GB download. Pluck reserves about 4.5 GB of private storage for the download and runtime cache. It uses the fixed Google-documented LiteRT Community distribution, delivered through Hugging Face over HTTPS, verifies its SHA-256, and never accepts a user-supplied model. After verification, stories work offline.") },
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

private fun LocalAiModelState.isOfflineReady(): Boolean =
    status == LocalAiInstallStatus.INSTALLED && verified

private fun formatBytes(value: Long): String = when {
    value <= 0 -> "Calculating"
    value < 1_000_000 -> "${value / 1_000} KB"
    else -> "%.1f GB".format(value / 1_000_000_000.0)
}
