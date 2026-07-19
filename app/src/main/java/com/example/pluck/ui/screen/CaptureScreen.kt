package com.example.pluck.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pluck.ui.components.LoadingView
import com.example.pluck.ui.components.PluckHapticEvent
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.rememberPluckHaptics
import com.example.pluck.viewmodel.CaptureViewModel

@Composable
fun CaptureScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: CaptureViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    AnimatedVisibility(cameraGranted, enter = fadeIn(), exit = fadeOut()) {
        CaptureExperience(
            saving = state.saving,
            hasLocation = locationGranted,
            createFile = viewModel::outputFile,
            onSaved = { file -> viewModel.save(file, locationGranted, onDone) },
            onError = { file -> file.delete() },
            onBack = onBack
        )
    }
    AnimatedVisibility(!cameraGranted, enter = fadeIn(), exit = fadeOut()) {
        PermissionExperience(onAllow = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, onBack = onBack)
    }
}

@Composable
private fun PermissionExperience(onAllow: () -> Unit, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(96.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CameraAlt, null, Modifier.size(44.dp), MaterialTheme.colorScheme.onPrimaryContainer) } }
            Text("The next scene needs a camera.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 24.dp))
            Text("Pluck only uses location when you allow it, to add a gentle place hint to your story.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            com.example.pluck.ui.components.AnimatedPrimaryButton("Allow camera", onAllow, Modifier.fillMaxWidth().padding(top = 28.dp))
            androidx.compose.material3.TextButton(onClick = onBack) { Text("Not now") }
        }
    }
}

@Composable
private fun CaptureExperience(saving: Boolean, hasLocation: Boolean, createFile: () -> java.io.File, onSaved: (java.io.File) -> Unit, onError: (java.io.File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberPluckHaptics()
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember { PreviewView(context) }
    var flash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(if (flash) 0.82f else 0f, label = "captureFlash")
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }, imageCapture)
            }
        }, executor)
        onDispose { runCatching { future.get().unbindAll() } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
        PluckTopAppBar(
            title = "Capture a place",
            subtitle = "One photo. One moment.",
            onBack = onBack,
            modifier = Modifier.statusBarsPadding(),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        if (hasLocation) Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 20.dp)
        ) { androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocationOn, null, Modifier.size(16.dp)); Text("Place hint on", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge) } }
        if (saving) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f)) { LoadingView("Saving this place…", Modifier.fillMaxSize()) }
        } else {
            IconButton(
                onClick = {
                    haptics.perform(PluckHapticEvent.Capture)
                    flash = true
                    val file = createFile()
                    imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { flash = false; onSaved(file) }
                        override fun onError(exception: ImageCaptureException) {
                            flash = false
                            haptics.perform(PluckHapticEvent.Error)
                            onError(file)
                        }
                    })
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp).size(80.dp).scale(1f)
            ) {
                Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary, tonalElevation = 6.dp, shadowElevation = 12.dp) { Box(contentAlignment = Alignment.Center) { Surface(Modifier.size(62.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)) {}; Icon(Icons.Rounded.CameraAlt, "Capture place", tint = MaterialTheme.colorScheme.onPrimary) } }
            }
        }
    }
}
